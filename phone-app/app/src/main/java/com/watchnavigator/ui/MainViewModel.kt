package com.watchnavigator.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.watchnavigator.data.DirectionsService
import com.watchnavigator.data.PlacesSearchService
import com.watchnavigator.model.LatLng
import com.watchnavigator.model.NavRoute
import com.watchnavigator.model.PlaceSuggestion
import com.watchnavigator.model.TravelMode
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

sealed class SuggestionsUiState {
    object Idle : SuggestionsUiState()
    object Loading : SuggestionsUiState()
    data class Success(val suggestions: List<PlaceSuggestion>) : SuggestionsUiState()
    data class Error(val message: String) : SuggestionsUiState()
}

sealed class RouteUiState {
    object Idle : RouteUiState()
    object Loading : RouteUiState()
    data class Success(val route: NavRoute) : RouteUiState()
    data class Error(val message: String) : RouteUiState()
}

@OptIn(FlowPreview::class)
class MainViewModel(
    private val placesSearchService: PlacesSearchService,
    private val directionsService: DirectionsService
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

    private val _travelMode = MutableStateFlow(TravelMode.DRIVING)
    val travelMode: StateFlow<TravelMode> = _travelMode.asStateFlow()

    private val _routeState = MutableStateFlow<RouteUiState>(RouteUiState.Idle)
    val routeState: StateFlow<RouteUiState> = _routeState.asStateFlow()

    private var searchJob: Job? = null
    private var selectJob: Job? = null
    private var routeJob: Job? = null

    init {
        _queryFlow
            .debounce(300)
            .distinctUntilChanged()
            .onEach { query ->
                if (query.isBlank()) {
                    _suggestionsState.value = SuggestionsUiState.Idle
                } else if (_selectedDestination.value?.primaryText != query) {
                    performSearch(query)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onQueryChanged(query: String) {
        _queryFlow.value = query
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _suggestionsState.value = SuggestionsUiState.Loading
            val result = placesSearchService.searchSuggestions(query)
            result.onSuccess { suggestions ->
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
        selectJob = viewModelScope.launch {
            val locResult = placesSearchService.fetchPlaceLocation(suggestion.placeId)
            if (_selectedDestination.value?.placeId != suggestion.placeId) return@launch
            locResult.onSuccess { latLng ->
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
        searchJob?.cancel()
        selectJob?.cancel()
        routeJob?.cancel()
        _selectedDestination.value = null
        _destinationLatLng.value = null
        _queryFlow.value = ""
        _suggestionsState.value = SuggestionsUiState.Idle
        _routeState.value = RouteUiState.Idle
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
        // If route is already displayed or was pending/errored due to missing GPS, calculate now
        if (prev == null && (_destinationLatLng.value != null || _selectedDestination.value != null)) {
            fetchRouteForSelectedDestination(_destinationLatLng.value, _selectedDestination.value?.placeId)
        }
    }

    fun retryRouteCalculation() {
        fetchRouteForSelectedDestination(_destinationLatLng.value, _selectedDestination.value?.placeId)
    }

    private fun fetchRouteForSelectedDestination(destinationLocation: LatLng?, destinationPlaceId: String?) {
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
        routeJob = viewModelScope.launch {
            _routeState.value = RouteUiState.Loading
            val mode = _travelMode.value
            val result = if (destinationLocation != null) {
                directionsService.getDirections(origin, destinationLocation, mode)
            } else {
                directionsService.getDirectionsByPlaceId(origin, destinationPlaceId!!, mode)
            }

            result.onSuccess { route ->
                _routeState.value = RouteUiState.Success(route)
            }.onFailure { error ->
                _routeState.value = RouteUiState.Error(error.message ?: "Failed to calculate route")
            }
        }
    }

    fun dismissError() {
        if (_routeState.value is RouteUiState.Error) {
            _routeState.value = RouteUiState.Idle
        }
        if (_suggestionsState.value is SuggestionsUiState.Error) {
            _suggestionsState.value = SuggestionsUiState.Idle
        }
    }

    class Factory(
        private val placesSearchService: PlacesSearchService,
        private val directionsService: DirectionsService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(placesSearchService, directionsService) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
