package com.watchnavigator.engine

import com.watchnavigator.data.DirectionsService
import com.watchnavigator.data.WearEngineService
import com.watchnavigator.model.LatLng
import com.watchnavigator.model.ManeuverType
import com.watchnavigator.model.NavRoute
import com.watchnavigator.model.TravelMode
import com.watchnavigator.model.WatchNavMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NavigationSessionManager(
    private var wearEngineService: WearEngineService? = null,
    private var directionsService: DirectionsService? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val engineFactory: (NavRoute, Double) -> NavigationEngine = { route, threshold ->
        NavigationEngine(route, stepAdvanceThresholdMeters = threshold)
    }
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val messageChannel = Channel<WatchNavMessage>(Channel.CONFLATED)

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

    private val _isRecalculating = MutableStateFlow(false)
    val isRecalculating: StateFlow<Boolean> = _isRecalculating.asStateFlow()

    private val _recalculationError = MutableStateFlow<String?>(null)
    val recalculationError: StateFlow<String?> = _recalculationError.asStateFlow()

    private var destinationName: String = ""
    private var destinationLocation: LatLng? = null
    private var currentSessionId: Long = 0L
    private var navigationEngine: NavigationEngine? = null

    // Rate-limiting and debounce configuration for off-route recalculation
    var consecutiveOffRouteThreshold: Int = 2
    var recalculationCooldownMs: Long = 10_000L
    var timeProvider: () -> Long = { System.currentTimeMillis() }

    private var consecutiveOffRouteCount: Int = 0
    private var lastRecalculationTimeMs: Long = 0L
    private var activeRecalculationJob: Job? = null

    private var currentVibrationThreshold: Double = NavigationEngine.DEFAULT_STEP_ADVANCE_THRESHOLD_METERS

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

    fun setDirectionsService(service: DirectionsService?) {
        this.directionsService = service
    }

    /**
     * Starts a new navigation session with the given route and travel mode.
     */
    fun startSession(
        route: NavRoute,
        travelMode: TravelMode,
        destName: String = route.destinationAddress,
        vibrationThresholdMeters: Double = NavigationEngine.DEFAULT_STEP_ADVANCE_THRESHOLD_METERS
    ) {
        currentSessionId++
        destinationName = destName.ifBlank { route.destinationAddress }
        destinationLocation = route.destination ?: route.steps.lastOrNull()?.endLocation
        _activeRoute.value = route
        _activeTravelMode.value = travelMode
        _isNavigating.value = true
        _isRecalculating.value = false
        _recalculationError.value = null
        consecutiveOffRouteCount = 0
        lastRecalculationTimeMs = 0L
        activeRecalculationJob?.cancel()
        activeRecalculationJob = null
        wearEngineService?.stopAutoReconnect()

        currentVibrationThreshold = vibrationThresholdMeters
        val engine = engineFactory(route, vibrationThresholdMeters)
        navigationEngine = engine

        if (route.steps.isNotEmpty()) {
            val initialStep = route.steps[0]
            val initialProgress =
                NavigationProgress(
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
            activeRecalculationJob?.cancel()
            _isRecalculating.value = false
            wearEngineService?.stopAutoReconnect()
            return
        }

        if (progress.isOffRoute) {
            consecutiveOffRouteCount++
            val now = timeProvider()
            val timeSinceLastRecalc = now - lastRecalculationTimeMs
            val shouldTrigger =
                !_isRecalculating.value &&
                    (consecutiveOffRouteCount >= consecutiveOffRouteThreshold || progress.offRouteDistanceMeters >= 100.0) &&
                    (lastRecalculationTimeMs == 0L || timeSinceLastRecalc >= recalculationCooldownMs)

            if (shouldTrigger && directionsService != null && destinationLocation != null) {
                triggerAutoRecalculation(location)
            } else {
                val msg =
                    WatchNavMessage.fromNavStep(
                        progress.currentStep,
                        progress.remainingDistanceToNextTurnMeters
                    )
                sendWatchMessage(msg)
            }
        } else {
            consecutiveOffRouteCount = 0
            _recalculationError.value = null
            val msg =
                WatchNavMessage.fromNavStep(
                    progress.currentStep,
                    progress.remainingDistanceToNextTurnMeters
                )
            sendWatchMessage(msg)
        }
    }

    /**
     * Triggers a single Directions API call to re-route from current location to destination.
     */
    fun triggerAutoRecalculation(currentLocation: LatLng) {
        val service = directionsService ?: return
        val dest = destinationLocation ?: return

        val sessionId = currentSessionId
        _isRecalculating.value = true
        lastRecalculationTimeMs = timeProvider()

        activeRecalculationJob?.cancel()
        activeRecalculationJob =
            scope.launch {
                try {
                    val result =
                        service.getDirections(
                            origin = currentLocation,
                            destination = dest,
                            mode = _activeTravelMode.value
                        )

                    result
                        .onSuccess { newRoute ->
                            if (sessionId != currentSessionId || !_isNavigating.value) return@onSuccess

                            _activeRoute.value = newRoute
                            _recalculationError.value = null
                            consecutiveOffRouteCount = 0

                            val newEngine = engineFactory(newRoute, currentVibrationThreshold)
                            navigationEngine = newEngine

                            val latestLoc = _lastLocation.value ?: currentLocation
                            val newProgress = newEngine.processLocation(latestLoc)
                            _navigationProgress.value = newProgress

                            if (newProgress.isArrived) {
                                val arrivalMsg = WatchNavMessage.arrival(destinationName)
                                sendWatchMessage(arrivalMsg)
                                _isNavigating.value = false
                                wearEngineService?.stopAutoReconnect()
                            } else {
                                val msg =
                                    WatchNavMessage.fromNavStep(
                                        newProgress.currentStep,
                                        newProgress.remainingDistanceToNextTurnMeters
                                    )
                                sendWatchMessage(msg)
                            }
                        }.onFailure { error ->
                            if (sessionId != currentSessionId || !_isNavigating.value) return@onFailure
                            _recalculationError.value = error.message ?: "Failed to recalculate route"
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (sessionId == currentSessionId && _isNavigating.value) {
                        _recalculationError.value = e.message ?: "Route recalculation failed"
                    }
                } finally {
                    if (sessionId == currentSessionId) {
                        _isRecalculating.value = false
                    }
                }
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
        wearEngineService?.stopAutoReconnect()
        _isRecalculating.value = false
        consecutiveOffRouteCount = 0
        activeRecalculationJob?.cancel()
        activeRecalculationJob = null
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
        result
            .onSuccess {
                if (service.isReconnecting.value) {
                    service.stopAutoReconnect()
                }
                if (_isNavigating.value || msg.turn == WatchNavMessage.TERMINAL_TURN || msg.turn == ManeuverType.ARRIVE.watchValue) {
                    _lastSentWatchMessage.value = msg
                    _watchSendError.value = null
                }
            }.onFailure { error ->
                _watchSendError.value = error.message ?: "Failed to send message to watch"
                if (_isNavigating.value) {
                    service.startAutoReconnect {
                        onWatchReconnected()
                    }
                }
            }
    }

    internal suspend fun onWatchReconnected() {
        if (!_isNavigating.value) return
        _watchSendError.value = null
        val progress = _navigationProgress.value
        val messageToSend =
            when {
                progress == null -> _lastSentWatchMessage.value
                progress.isArrived -> WatchNavMessage.arrival(destinationName)
                else ->
                    WatchNavMessage.fromNavStep(
                        progress.currentStep,
                        progress.remainingDistanceToNextTurnMeters
                    )
            }
        if (messageToSend != null) {
            val service = wearEngineService ?: return
            val result = service.sendNavMessage(messageToSend)
            result
                .onSuccess {
                    if (_isNavigating.value) {
                        _lastSentWatchMessage.value = messageToSend
                        _watchSendError.value = null
                    }
                }.onFailure { error ->
                    if (_isNavigating.value) {
                        _watchSendError.value = error.message ?: "Failed to send message to watch"
                        service.startAutoReconnect {
                            onWatchReconnected()
                        }
                    }
                }
        }
    }

    companion object {
        @Volatile
        private var instance: NavigationSessionManager? = null

        fun getInstance(
            wearEngineService: WearEngineService? = null,
            directionsService: DirectionsService? = null,
            dispatcher: CoroutineDispatcher = Dispatchers.Main
        ): NavigationSessionManager =
            instance ?: synchronized(this) {
                instance ?: NavigationSessionManager(wearEngineService, directionsService, dispatcher).also {
                    instance = it
                }
            }

        // For unit testing
        fun setInstanceForTesting(manager: NavigationSessionManager?) {
            instance = manager
        }
    }
}
