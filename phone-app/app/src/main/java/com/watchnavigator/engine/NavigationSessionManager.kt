package com.watchnavigator.engine

import com.watchnavigator.data.WearEngineService
import com.watchnavigator.model.LatLng
import com.watchnavigator.model.NavRoute
import com.watchnavigator.model.TravelMode
import com.watchnavigator.model.WatchNavMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NavigationSessionManager(
    private var wearEngineService: WearEngineService? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val messageChannel = Channel<WatchNavMessage>(Channel.UNLIMITED)

    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating.asStateFlow()

    private val _activeRoute = MutableStateFlow<NavRoute?>(null)
    val activeRoute: StateFlow<NavRoute?> = _activeRoute.asStateFlow()

    private val _activeTravelMode = MutableStateFlow(TravelMode.DRIVING)
    val activeTravelMode: StateFlow<TravelMode> = _activeTravelMode.asStateFlow()

    private val _navigationProgress = MutableStateFlow<NavigationProgress?>(null)
    val navigationProgress: StateFlow<NavigationProgress?> = _navigationProgress.asStateFlow()

    private val _lastLocation = MutableStateFlow<LatLng?>(null)
    val lastLocation: StateFlow<LatLng?> = _lastLocation.asStateFlow()

    private val _lastSentWatchMessage = MutableStateFlow<WatchNavMessage?>(null)
    val lastSentWatchMessage: StateFlow<WatchNavMessage?> = _lastSentWatchMessage.asStateFlow()

    private val _watchSendError = MutableStateFlow<String?>(null)
    val watchSendError: StateFlow<String?> = _watchSendError.asStateFlow()

    private var destinationName: String = ""
    private var navigationEngine: NavigationEngine? = null

    init {
        scope.launch {
            for (msg in messageChannel) {
                deliverMessage(msg)
            }
        }
    }

    fun setWearEngineService(service: WearEngineService?) {
        this.wearEngineService = service
    }

    /**
     * Starts a new navigation session with the given route and travel mode.
     */
    fun startSession(
        route: NavRoute,
        travelMode: TravelMode,
        destName: String = route.destinationAddress
    ) {
        destinationName = destName.ifBlank { route.destinationAddress }
        _activeRoute.value = route
        _activeTravelMode.value = travelMode
        _isNavigating.value = true

        val engine = NavigationEngine(route)
        navigationEngine = engine

        if (route.steps.isNotEmpty()) {
            val initialStep = route.steps[0]
            val initialProgress = NavigationProgress(
                currentStepIndex = 0,
                currentStep = initialStep,
                remainingDistanceToNextTurnMeters = initialStep.distanceMeters,
                totalRemainingDistanceMeters = route.totalDistanceMeters,
                isArrived = false
            )
            _navigationProgress.value = initialProgress
            sendWatchMessage(WatchNavMessage.fromNavStep(initialStep, initialStep.distanceMeters))
        }
    }

    /**
     * Processes a new GPS location update from the continuous tracking provider.
     */
    fun onLocationUpdate(location: LatLng) {
        if (!_isNavigating.value) return
        val engine = navigationEngine ?: return

        _lastLocation.value = location
        val progress = engine.processLocation(location)
        _navigationProgress.value = progress

        if (progress.isArrived) {
            val arrivalMsg = WatchNavMessage.arrival(destinationName)
            sendWatchMessage(arrivalMsg)
            _isNavigating.value = false
        } else {
            val msg = WatchNavMessage.fromNavStep(
                progress.currentStep,
                progress.remainingDistanceToNextTurnMeters
            )
            sendWatchMessage(msg)
        }
    }

    /**
     * Stops the active navigation session and sends the stop signal to the watch.
     */
    fun stopSession() {
        if (_isNavigating.value) {
            _isNavigating.value = false
            sendWatchMessage(WatchNavMessage.stop())
        }
        navigationEngine = null
    }

    fun clearWatchSendError() {
        _watchSendError.value = null
    }

    fun sendWatchMessage(msg: WatchNavMessage) {
        messageChannel.trySend(msg)
    }

    private suspend fun deliverMessage(msg: WatchNavMessage) {
        val service = wearEngineService
        if (service == null) {
            _lastSentWatchMessage.value = msg
            _watchSendError.value = null
            return
        }
        val result = service.sendNavMessage(msg)
        result.onSuccess {
            _lastSentWatchMessage.value = msg
            _watchSendError.value = null
        }.onFailure { error ->
            _watchSendError.value = error.message ?: "Failed to send message to watch"
        }
    }

    companion object {
        @Volatile
        private var instance: NavigationSessionManager? = null

        fun getInstance(
            wearEngineService: WearEngineService? = null,
            dispatcher: CoroutineDispatcher = Dispatchers.Main
        ): NavigationSessionManager {
            return instance ?: synchronized(this) {
                instance ?: NavigationSessionManager(wearEngineService, dispatcher).also {
                    instance = it
                }
            }
        }

        // For unit testing
        fun setInstanceForTesting(manager: NavigationSessionManager?) {
            instance = manager
        }
    }
}
