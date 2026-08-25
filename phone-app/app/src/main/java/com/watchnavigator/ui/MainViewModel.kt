package com.watchnavigator.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.watchnavigator.data.DirectionsService
import com.watchnavigator.data.PlacesSearchService
import com.watchnavigator.data.PreferencesRepository
import com.watchnavigator.data.WearEngineService
import com.watchnavigator.engine.NavigationProgress
import com.watchnavigator.engine.NavigationSessionManager
import com.watchnavigator.model.LatLng
import com.watchnavigator.model.NavRoute
import com.watchnavigator.model.PlaceSuggestion
import com.watchnavigator.model.TravelMode
import com.watchnavigator.model.WatchConnectionState
import com.watchnavigator.model.WatchNavMessage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

sealed class SuggestionsUiState {
    object Idle : SuggestionsUiState()

    object Loading : SuggestionsUiState()

    data class Success(
        val suggestions: List<PlaceSuggestion>
    ) : SuggestionsUiState()

    data class Error(
        val message: String
    ) : SuggestionsUiState()
}

sealed class RouteUiState {
    object Idle : RouteUiState()

    object Loading : RouteUiState()

    data class Success(
        val route: NavRoute
    ) : RouteUiState()

    data class Error(
        val message: String
    ) : RouteUiState()
}

@OptIn(FlowPreview::class)
class MainViewModel(
    private val placesSearchService: PlacesSearchService,
    private val directionsService: DirectionsService,
    private val wearEngineService: WearEngineService? = null,
    private val preferencesRepository: PreferencesRepository,
    val sessionManager: NavigationSessionManager = NavigationSessionManager.getInstance(wearEngineService, directionsService)
) : ViewModel() {
    private val _queryFlow = MutableStateFlow("")
    val queryFlow: StateFlow<String> = _queryFlow.asStateFlow()

    private val _suggestionsState = MutableStateFlow<SuggestionsUiState>(SuggestionsUiState.Idle)
    val suggestionsState: StateFlow<SuggestionsUiState> = _suggestionsState.asStateFlow()

    private val _selectedDestination = MutableStateFlow<PlaceSuggestion?>(null)
    val selectedDestination: StateFlow<PlaceSuggestion?> = _selectedDestination.asStateFlow()

    private val _destinationLatLng = MutableStateFlow<LatLng?>(null)
    val destinationLatLng: StateFlow<LatLng?> = _destinationLatLng.asStateFlow()

    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation: StateFlow<LatLng?> = _currentLocation.asStateFlow()

    private val _travelMode = MutableStateFlow(preferencesRepository.getUserPreferences().defaultTravelMode)
    val travelMode: StateFlow<TravelMode> = _travelMode.asStateFlow()

    private val _routeState = MutableStateFlow<RouteUiState>(RouteUiState.Idle)
    val routeState: StateFlow<RouteUiState> = _routeState.asStateFlow()

    val isNavigating: StateFlow<Boolean> = sessionManager.isNavigating
    val navigationProgress: StateFlow<NavigationProgress?> = sessionManager.navigationProgress
    val lastSentWatchMessage: StateFlow<WatchNavMessage?> = sessionManager.lastSentWatchMessage
    val watchSendError: StateFlow<String?> = sessionManager.watchSendError

    val isRecalculating: StateFlow<Boolean> = sessionManager.isRecalculating
    val recalculationError: StateFlow<String?> = sessionManager.recalculationError

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    val watchConnectionState: StateFlow<WatchConnectionState> =
        wearEngineService?.connectionState ?: MutableStateFlow(WatchConnectionState.Disconnected()).asStateFlow()

    private var searchJob: Job? = null
    private var selectJob: Job? = null
    private var routeJob: Job? = null
    private var watchConnectionJob: Job? = null

    init {
        sessionManager.setWearEngineService(wearEngineService)
        sessionManager.setDirectionsService(directionsService)
        _queryFlow
            .debounce(300)
            .distinctUntilChanged()
            .onEach { query ->
                if (query.isBlank()) {
                    _suggestionsState.value = SuggestionsUiState.Idle
                } else if (_selectedDestination.value?.primaryText != query) {
                    performSearch(query)
                }
            }.launchIn(viewModelScope)

        checkWatchConnection()

        // Keep local step index synced with session progress
        viewModelScope.launch {
            sessionManager.navigationProgress.collect { progress ->
                if (progress != null) {
                    _currentStepIndex.value = progress.currentStepIndex
                }
            }
        }

        // Keep route UI state synchronized when session recalculates active route
        viewModelScope.launch {
            sessionManager.activeRoute.collect { newRoute ->
                if (newRoute != null && sessionManager.isNavigating.value) {
                    _routeState.value = RouteUiState.Success(newRoute)
                }
            }
        }
    }

    fun checkWatchConnection() {
        if (watchConnectionJob?.isActive == true) return
        sessionManager.clearWatchSendError()
        watchConnectionJob =
            viewModelScope.launch {
                wearEngineService?.checkConnection()
            }
    }

    fun requestWatchPermission() {
        if (watchConnectionJob?.isActive == true) return
        sessionManager.clearWatchSendError()
        watchConnectionJob = viewModelScope.launch {
            wearEngineService?.requestPermission()
        }
    }

    fun connectOrRequestWatchPermission() {
        if (watchConnectionJob?.isActive == true) return
        sessionManager.clearWatchSendError()
        watchConnectionJob = viewModelScope.launch {
            if (watchConnectionState.value is WatchConnectionState.Unauthorized) {
                wearEngineService?.requestPermission()
            } else {
                wearEngineService?.checkConnection()
            }
        }
    }

    fun onQueryChanged(query: String) {
        _queryFlow.value = query
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob =
            viewModelScope.launch {
                _suggestionsState.value = SuggestionsUiState.Loading
                val result = placesSearchService.searchSuggestions(query)
                result
                    .onSuccess { suggestions ->
                        _suggestionsState.value = SuggestionsUiState.Success(suggestions)
                    }.onFailure { error ->
                        _suggestionsState.value = SuggestionsUiState.Error(error.message ?: "Failed to find suggestions")
                    }
            }
    }

    fun selectSuggestion(suggestion: PlaceSuggestion) {
        _selectedDestination.value = suggestion
        _destinationLatLng.value = null
        _queryFlow.value = suggestion.primaryText
        _suggestionsState.value = SuggestionsUiState.Idle

        selectJob?.cancel()
        routeJob?.cancel()
        selectJob =
            viewModelScope.launch {
                val locResult = placesSearchService.fetchPlaceLocation(suggestion.placeId)
                if (_selectedDestination.value?.placeId != suggestion.placeId) return@launch
                locResult
                    .onSuccess { latLng ->
                        if (_selectedDestination.value?.placeId != suggestion.placeId) return@onSuccess
                        _destinationLatLng.value = latLng
                        placesSearchService.createNewSessionToken()
                        fetchRouteForSelectedDestination(latLng, suggestion.placeId)
                    }.onFailure { error ->
                        if (_selectedDestination.value?.placeId != suggestion.placeId) return@onFailure
                        _routeState.value = RouteUiState.Error("Could not get location for ${suggestion.primaryText}: ${error.message}")
                    }
            }
    }

    fun clearSelectedDestination() {
        stopNavigation()
        searchJob?.cancel()
        selectJob?.cancel()
        routeJob?.cancel()
        _selectedDestination.value = null
        _destinationLatLng.value = null
        _queryFlow.value = ""
        _suggestionsState.value = SuggestionsUiState.Idle
        _routeState.value = RouteUiState.Idle
    }

    fun refreshTravelModeFromPreferences() {
        if (!sessionManager.isNavigating.value) {
            setTravelMode(preferencesRepository.getUserPreferences().defaultTravelMode)
        }
    }

    fun setTravelMode(mode: TravelMode) {
        if (_travelMode.value != mode) {
            _travelMode.value = mode
            val destLatLng = _destinationLatLng.value
            val destSuggestion = _selectedDestination.value
            if (destLatLng != null || destSuggestion != null) {
                fetchRouteForSelectedDestination(destLatLng, destSuggestion?.placeId)
            }
        }
    }

    fun setCurrentLocation(latLng: LatLng) {
        val prev = _currentLocation.value
        _currentLocation.value = latLng
        if (sessionManager.isNavigating.value) {
            sessionManager.onLocationUpdate(latLng)
        }
        // If route was pending due to missing GPS, calculate now
        if (prev == null && (_destinationLatLng.value != null || _selectedDestination.value != null)) {
            fetchRouteForSelectedDestination(_destinationLatLng.value, _selectedDestination.value?.placeId)
        }
    }

    fun retryRouteCalculation() {
        fetchRouteForSelectedDestination(_destinationLatLng.value, _selectedDestination.value?.placeId)
    }

    private fun fetchRouteForSelectedDestination(
        destinationLocation: LatLng?,
        destinationPlaceId: String?
    ) {
        val origin = _currentLocation.value
        if (origin == null) {
            _routeState.value = RouteUiState.Error("Current GPS location not available. Please enable location.")
            return
        }

        if (destinationLocation == null && destinationPlaceId == null) {
            _routeState.value = RouteUiState.Error("Destination not specified.")
            return
        }

        routeJob?.cancel()
        routeJob =
            viewModelScope.launch {
                _routeState.value = RouteUiState.Loading
                val mode = _travelMode.value
                val result =
                    if (destinationLocation != null) {
                        directionsService.getDirections(origin, destinationLocation, mode)
                    } else {
                        directionsService.getDirectionsByPlaceId(origin, destinationPlaceId!!, mode)
                    }

                result
                    .onSuccess { route ->
                        _routeState.value = RouteUiState.Success(route)
                        _currentStepIndex.value = 0
                    }.onFailure { error ->
                        _routeState.value = RouteUiState.Error(error.message ?: "Failed to calculate route")
                    }
            }
    }

    fun startNavigation() {
        val currentRouteState = _routeState.value
        if (currentRouteState is RouteUiState.Success && currentRouteState.route.steps.isNotEmpty()) {
            val destName = _selectedDestination.value?.primaryText ?: currentRouteState.route.destinationAddress
            val mode = _travelMode.value
            val vibrationThreshold =
                preferencesRepository
                    .getUserPreferences()
                    .vibrationThresholdMetersFor(mode)
                    .toDouble()
            sessionManager.startSession(currentRouteState.route, mode, destName, vibrationThreshold)
        }
    }

    fun stopNavigation() {
        sessionManager.stopSession()
    }

    fun updateNavigationStep(
        stepIndex: Int,
        remainingDistanceMeters: Int? = null
    ) {
        val currentRouteState = _routeState.value
        if (currentRouteState is RouteUiState.Success) {
            val steps = currentRouteState.route.steps
            if (stepIndex in steps.indices) {
                _currentStepIndex.value = stepIndex
                if (isNavigating.value) {
                    dispatchStepAt(steps, stepIndex, remainingDistanceMeters)
                }
            }
        }
    }

    fun sendCurrentStepToWatch(remainingDistanceMeters: Int? = null) {
        val currentRouteState = _routeState.value
        if (currentRouteState is RouteUiState.Success) {
            val steps = currentRouteState.route.steps
            val index = _currentStepIndex.value
            if (index in steps.indices) {
                dispatchStepAt(steps, index, remainingDistanceMeters)
            }
        }
    }

    private fun dispatchStepAt(
        steps: List<com.watchnavigator.model.NavStep>,
        index: Int,
        remainingDistanceMeters: Int?
    ) {
        val step = steps[index]
        val dist = remainingDistanceMeters ?: step.distanceMeters
        val msg = WatchNavMessage.fromNavStep(step, dist)
        sessionManager.sendWatchMessage(msg)
    }

    fun sendArrivalToWatch() {
        val destName = _selectedDestination.value?.primaryText ?: ""
        val msg = WatchNavMessage.arrival(destName)
        sessionManager.sendWatchMessage(msg)
    }

    fun dismissError() {
        if (_routeState.value is RouteUiState.Error) {
            _routeState.value = RouteUiState.Idle
        }
        if (_suggestionsState.value is SuggestionsUiState.Error) {
            _suggestionsState.value = SuggestionsUiState.Idle
        }
    }

    override fun onCleared() {
        super.onCleared()
        wearEngineService?.release()
    }

    class Factory(
        private val placesSearchService: PlacesSearchService,
        private val directionsService: DirectionsService,
        private val wearEngineService: WearEngineService? = null,
        private val preferencesRepository: PreferencesRepository,
        private val sessionManager: NavigationSessionManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                val sm = sessionManager ?: NavigationSessionManager.getInstance(wearEngineService, directionsService)
                return MainViewModel(placesSearchService, directionsService, wearEngineService, preferencesRepository, sm) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
