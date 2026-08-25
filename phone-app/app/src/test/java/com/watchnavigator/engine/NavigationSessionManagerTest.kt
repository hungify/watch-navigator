package com.watchnavigator.engine

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.watchnavigator.data.DirectionsService
import com.watchnavigator.data.WearEngineService
import com.watchnavigator.model.LatLng
import com.watchnavigator.model.ManeuverType
import com.watchnavigator.model.NavRoute
import com.watchnavigator.model.NavStep
import com.watchnavigator.model.TravelMode
import com.watchnavigator.model.WatchConnectionState
import com.watchnavigator.model.WatchNavMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationSessionManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeWearEngineService: FakeWearEngineService
    private lateinit var fakeDirectionsService: FakeDirectionsService
    private lateinit var sessionManager: NavigationSessionManager
    private lateinit var sampleRoute: NavRoute

    private val p0 = LatLng(21.0000, 105.0000)
    private val p1 = LatLng(21.0010, 105.0000)
    private val p2 = LatLng(21.0020, 105.0000)
    private val p3 = LatLng(21.0020, 105.0040)

    @Before
    fun setUp() {
        fakeWearEngineService = FakeWearEngineService()
        fakeDirectionsService = FakeDirectionsService()
        sessionManager = NavigationSessionManager(fakeWearEngineService, fakeDirectionsService, testDispatcher)
        val step0 = NavStep(
            instruction = "Head north on Street A",
            streetName = "Street A",
            maneuver = ManeuverType.STRAIGHT,
            distanceMeters = 222,
            durationSeconds = 60,
            startLocation = p0,
            endLocation = p2,
            polylinePoints = listOf(p0, p1, p2)
        )

        val step1 = NavStep(
            instruction = "Turn right onto Street B",
            streetName = "Street B",
            maneuver = ManeuverType.TURN_RIGHT,
            distanceMeters = 415,
            durationSeconds = 90,
            startLocation = p2,
            endLocation = p3,
            polylinePoints = listOf(p2, p3)
        )

        sampleRoute = NavRoute(
            origin = p0,
            destination = p3,
            destinationAddress = "Target Hanoi",
            totalDistanceMeters = 637,
            totalDurationSeconds = 150,
            travelMode = TravelMode.DRIVING,
            overviewPolyline = listOf(p0, p1, p2, p3),
            steps = listOf(step0, step1)
        )
    }

    @Test
    fun startSession_initializesStateAndSendsFirstStepToWatch() = runTest(testDispatcher) {
        sessionManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi")
        advanceUntilIdle()

        assertThat(sessionManager.isNavigating.value).isTrue()
        assertThat(sessionManager.activeRoute.value).isEqualTo(sampleRoute)
        assertThat(sessionManager.activeTravelMode.value).isEqualTo(TravelMode.DRIVING)

        val progress = sessionManager.navigationProgress.value
        assertThat(progress).isNotNull()
        assertThat(progress!!.currentStepIndex).isEqualTo(0)
        assertThat(progress.currentStep.streetName).isEqualTo("Street A")

        assertThat(fakeWearEngineService.sentMessages).hasSize(1)
        val msg = fakeWearEngineService.sentMessages.first()
        assertThat(msg.turn).isEqualTo("straight")
        assertThat(msg.street).isEqualTo("Street A")
    }

    @Test
    fun onLocationUpdate_advancesStepsAndDispatchesTurnUpdates() = runTest(testDispatcher) {
        sessionManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi")
        advanceUntilIdle()

        // Move to midway on step 0
        sessionManager.onLocationUpdate(p1)
        advanceUntilIdle()

        assertThat(sessionManager.lastLocation.value).isEqualTo(p1)
        val progress1 = sessionManager.navigationProgress.value
        assertThat(progress1).isNotNull()
        assertThat(progress1!!.currentStepIndex).isEqualTo(0)
        assertThat(progress1.remainingDistanceToNextTurnMeters).isLessThan(200)

        // Move near step 1 turn
        sessionManager.onLocationUpdate(p2)
        advanceUntilIdle()

        val progress2 = sessionManager.navigationProgress.value
        assertThat(progress2).isNotNull()
        assertThat(progress2!!.currentStepIndex).isEqualTo(1)
        assertThat(progress2.currentStep.maneuver).isEqualTo(ManeuverType.TURN_RIGHT)

        val lastSent = fakeWearEngineService.sentMessages.last()
        assertThat(lastSent.turn).isEqualTo("right")
        assertThat(lastSent.street).isEqualTo("Street B")
    }

    @Test
    fun onLocationUpdate_destinationArrival_sendsArrivalAndFinishesSession() = runTest(testDispatcher) {
        sessionManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi")
        advanceUntilIdle()

        // Move to destination p3
        sessionManager.onLocationUpdate(p3)
        advanceUntilIdle()

        val progress = sessionManager.navigationProgress.value
        assertThat(progress).isNotNull()
        assertThat(progress!!.isArrived).isTrue()
        assertThat(sessionManager.isNavigating.value).isFalse()

        val lastSent = fakeWearEngineService.sentMessages.last()
        assertThat(lastSent.turn).isEqualTo("arrive")
        assertThat(lastSent.street).isEqualTo("Target Hanoi")
        assertThat(lastSent.distanceMeters).isEqualTo(0)
    }

    @Test
    fun stopSession_sendsStopMessageToWatch() = runTest(testDispatcher) {
        sessionManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi")
        advanceUntilIdle()

        sessionManager.stopSession()
        advanceUntilIdle()

        assertThat(sessionManager.isNavigating.value).isFalse()
        val lastSent = fakeWearEngineService.sentMessages.last()
        assertThat(lastSent.turn).isEqualTo("stop")
    }

    @Test
    fun startSession_withCustomVibrationThreshold_passesThresholdToEngineFactory() = runTest(testDispatcher) {
        var capturedThreshold: Double? = null
        val factoryManager = NavigationSessionManager(
            fakeWearEngineService,
            fakeDirectionsService,
            testDispatcher
        ) { route, threshold ->
            capturedThreshold = threshold
            NavigationEngine(route, stepAdvanceThresholdMeters = threshold)
        }

        factoryManager.startSession(sampleRoute, TravelMode.WALKING, "Target Hanoi", vibrationThresholdMeters = 50.0)
        advanceUntilIdle()

        assertThat(capturedThreshold).isEqualTo(50.0)
    }

    @Test
    fun startSession_withoutExplicitThreshold_usesEngineDefault() = runTest(testDispatcher) {
        var capturedThreshold: Double? = null
        val factoryManager = NavigationSessionManager(
            fakeWearEngineService,
            fakeDirectionsService,
            testDispatcher
        ) { route, threshold ->
            capturedThreshold = threshold
            NavigationEngine(route, stepAdvanceThresholdMeters = threshold)
        }

        factoryManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi")
        advanceUntilIdle()

        assertThat(capturedThreshold).isEqualTo(NavigationEngine.DEFAULT_STEP_ADVANCE_THRESHOLD_METERS)
    }

    @Test
    fun sendError_capturesWatchSendFailureAndStartsAutoReconnect() = runTest(testDispatcher) {
        fakeWearEngineService.sendResultToReturn = Result.failure(Exception("Bluetooth lost"))

        sessionManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi")
        advanceUntilIdle()

        assertThat(sessionManager.watchSendError.value).isEqualTo("Bluetooth lost")
        assertThat(fakeWearEngineService.autoReconnectStarted).isTrue()
    }

    @Test
    fun onWatchReconnected_resendsLatestNavigationInstructionImmediately() = runTest(testDispatcher) {
        fakeWearEngineService.sendResultToReturn = Result.failure(Exception("Bluetooth lost"))

        sessionManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi")
        advanceUntilIdle()

        assertThat(fakeWearEngineService.autoReconnectStarted).isTrue()
        assertThat(sessionManager.watchSendError.value).isNotNull()

        // Move to next step (p2) while disconnected
        sessionManager.onLocationUpdate(p2)
        advanceUntilIdle()

        // Reconnect succeeds
        fakeWearEngineService.sendResultToReturn = Result.success(Unit)
        fakeWearEngineService.onReconnectedCallback?.invoke()
        advanceUntilIdle()

        assertThat(sessionManager.watchSendError.value).isNull()
        val lastSent = fakeWearEngineService.sentMessages.last()
        assertThat(lastSent.turn).isEqualTo("right")
        assertThat(lastSent.street).isEqualTo("Street B")
    }

    @Test
    fun locationUpdatesDuringDisconnect_maintainActiveSessionStateWithoutDataLoss() = runTest(testDispatcher) {
        fakeWearEngineService.sendResultToReturn = Result.failure(Exception("Bluetooth dropped"))

        sessionManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi")
        advanceUntilIdle()

        assertThat(sessionManager.isNavigating.value).isTrue()

        // Send intermediate locations
        sessionManager.onLocationUpdate(LatLng(21.0002, 105.0000))
        advanceUntilIdle()

        assertThat(sessionManager.isNavigating.value).isTrue()
        assertThat(sessionManager.navigationProgress.value).isNotNull()
        assertThat(sessionManager.navigationProgress.value?.currentStepIndex).isEqualTo(0)

        sessionManager.onLocationUpdate(p2)
        advanceUntilIdle()
        assertThat(sessionManager.navigationProgress.value?.currentStepIndex).isEqualTo(1)
        assertThat(sessionManager.navigationProgress.value?.currentStep?.streetName).isEqualTo("Street B")
    }

    @Test
    fun stopSession_stopsAutoReconnect() = runTest(testDispatcher) {
        fakeWearEngineService.sendResultToReturn = Result.failure(Exception("Bluetooth lost"))
        sessionManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi")
        advanceUntilIdle()

        assertThat(fakeWearEngineService.autoReconnectStarted).isTrue()

        sessionManager.stopSession()
        advanceUntilIdle()

        assertThat(fakeWearEngineService.autoReconnectStopped).isTrue()
    }

    @Test
    fun arrival_stopsAutoReconnect() = runTest(testDispatcher) {
        fakeWearEngineService.sendResultToReturn = Result.failure(Exception("Bluetooth lost"))
        sessionManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi")
        advanceUntilIdle()

        assertThat(fakeWearEngineService.autoReconnectStarted).isTrue()

        // Arrival at destination
        sessionManager.onLocationUpdate(p3)
        advanceUntilIdle()

        assertThat(sessionManager.isNavigating.value).isFalse()
        assertThat(fakeWearEngineService.autoReconnectStopped).isTrue()
    }

    @Test
    fun onWatchReconnected_whenNotNavigating_doesNotSendMessage() = runTest(testDispatcher) {
        fakeWearEngineService.sentMessages.clear()

        // sessionManager is not navigating
        assertThat(sessionManager.isNavigating.value).isFalse()
        sessionManager.onWatchReconnected()
        advanceUntilIdle()

        assertThat(fakeWearEngineService.sentMessages).isEmpty()
    }

    @Test
    fun onWatchReconnected_whenResendFails_restartsAutoReconnect() = runTest(testDispatcher) {
        fakeWearEngineService.sendResultToReturn = Result.failure(Exception("Bluetooth lost"))
        sessionManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi")
        advanceUntilIdle()

        assertThat(fakeWearEngineService.autoReconnectStarted).isTrue()

        // Reset flag to track re-trigger
        fakeWearEngineService.autoReconnectStarted = false
        fakeWearEngineService.sendResultToReturn = Result.failure(Exception("Still failing"))

        fakeWearEngineService.onReconnectedCallback?.invoke()
        advanceUntilIdle()

        assertThat(sessionManager.watchSendError.value).isEqualTo("Still failing")
        assertThat(fakeWearEngineService.autoReconnectStarted).isTrue()
    }

    @Test
    fun stopSession_calledWhenAlreadyStopped_stillStopsAutoReconnect() = runTest(testDispatcher) {
        fakeWearEngineService.autoReconnectStopped = false
        assertThat(sessionManager.isNavigating.value).isFalse()

        sessionManager.stopSession()
        advanceUntilIdle()

        assertThat(fakeWearEngineService.autoReconnectStopped).isTrue()
    }

    @Test
    fun successfulSend_whenReconnecting_stopsAutoReconnect() = runTest(testDispatcher) {
        fakeWearEngineService.sendResultToReturn = Result.failure(Exception("Bluetooth lost"))
        sessionManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi")
        advanceUntilIdle()

        assertThat(fakeWearEngineService.autoReconnectStarted).isTrue()
        assertThat(fakeWearEngineService.isReconnecting.value).isTrue()

        // Subsequent send succeeds (e.g. location update)
        fakeWearEngineService.sendResultToReturn = Result.success(Unit)
        sessionManager.onLocationUpdate(p2)
        advanceUntilIdle()

        assertThat(fakeWearEngineService.autoReconnectStopped).isTrue()
        assertThat(fakeWearEngineService.isReconnecting.value).isFalse()
        assertThat(sessionManager.watchSendError.value).isNull()
    }

    @Test
    fun arrival_updatesLastSentWatchMessageAndClearsError() = runTest(testDispatcher) {
        sessionManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi")
        advanceUntilIdle()

        // Arrival at destination
        sessionManager.onLocationUpdate(p3)
        advanceUntilIdle()

        assertThat(sessionManager.isNavigating.value).isFalse()
        assertThat(sessionManager.lastSentWatchMessage.value?.turn).isEqualTo(ManeuverType.ARRIVE.watchValue)
        assertThat(sessionManager.watchSendError.value).isNull()
    }

    @Test
    fun offRoute_firstFixUnderThreshold_doesNotTriggerRecalculation() = runTest(testDispatcher) {
        sessionManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi")
        advanceUntilIdle()

        // Off-route by ~70m (east of p1)
        val offRouteLoc = LatLng(21.0010, 105.0007)
        sessionManager.onLocationUpdate(offRouteLoc)
        advanceUntilIdle()

        // Only 1 fix, threshold is 2 -> not yet triggered
        assertThat(fakeDirectionsService.requestedCount).isEqualTo(0)
        assertThat(sessionManager.isRecalculating.value).isFalse()
    }

    @Test
    fun offRoute_meetsConsecutiveThreshold_triggersRecalculationAndUpdatesRoute() = runTest(testDispatcher) {
        val reroutedStep = NavStep(
            instruction = "Turn left onto New Route",
            streetName = "New Route St",
            maneuver = ManeuverType.TURN_LEFT,
            distanceMeters = 300,
            durationSeconds = 80,
            startLocation = LatLng(21.0010, 105.0007),
            endLocation = p3,
            polylinePoints = listOf(LatLng(21.0010, 105.0007), p3)
        )
        val recalculatedRoute = NavRoute(
            origin = LatLng(21.0010, 105.0007),
            destination = p3,
            destinationAddress = "Target Hanoi",
            totalDistanceMeters = 300,
            totalDurationSeconds = 80,
            travelMode = TravelMode.DRIVING,
            overviewPolyline = listOf(LatLng(21.0010, 105.0007), p3),
            steps = listOf(reroutedStep)
        )
        fakeDirectionsService.routeToReturn = recalculatedRoute

        sessionManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi")
        advanceUntilIdle()

        val offRouteLoc1 = LatLng(21.0010, 105.0007)
        val offRouteLoc2 = LatLng(21.0011, 105.0008)

        sessionManager.onLocationUpdate(offRouteLoc1)
        advanceUntilIdle()
        assertThat(fakeDirectionsService.requestedCount).isEqualTo(0)

        // 2nd consecutive off-route fix -> triggers recalculation
        sessionManager.onLocationUpdate(offRouteLoc2)
        advanceUntilIdle()

        assertThat(fakeDirectionsService.requestedCount).isEqualTo(1)
        assertThat(sessionManager.activeRoute.value).isEqualTo(recalculatedRoute)
        assertThat(sessionManager.navigationProgress.value?.currentStep?.streetName).isEqualTo("New Route St")

        // Check that new maneuver was sent immediately to watch
        val lastSent = fakeWearEngineService.sentMessages.last()
        assertThat(lastSent.turn).isEqualTo("left")
        assertThat(lastSent.street).isEqualTo("New Route St")
    }

    @Test
    fun offRoute_recalculation_preservesCustomVibrationThresholdInEngineFactory() = runTest(testDispatcher) {
        var latestFactoryThreshold: Double? = null
        val customManager = NavigationSessionManager(
            fakeWearEngineService,
            fakeDirectionsService,
            testDispatcher
        ) { route, threshold ->
            latestFactoryThreshold = threshold
            NavigationEngine(route, stepAdvanceThresholdMeters = threshold)
        }

        fakeDirectionsService.routeToReturn = sampleRoute
        customManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi", vibrationThresholdMeters = 75.0)
        advanceUntilIdle()
        assertThat(latestFactoryThreshold).isEqualTo(75.0)

        // Force immediate recalculation (>100m deviation)
        val farOffRouteLoc = LatLng(21.0010, 105.0025)
        customManager.onLocationUpdate(farOffRouteLoc)
        advanceUntilIdle()

        assertThat(fakeDirectionsService.requestedCount).isEqualTo(1)
        assertThat(latestFactoryThreshold).isEqualTo(75.0)
    }

    @Test
    fun offRoute_largeDeviationOver100m_triggersImmediateRecalculation() = runTest(testDispatcher) {
        fakeDirectionsService.routeToReturn = sampleRoute

        sessionManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi")
        advanceUntilIdle()

        // Point >150m away from route
        val farOffRouteLoc = LatLng(21.0010, 105.0025)
        sessionManager.onLocationUpdate(farOffRouteLoc)
        advanceUntilIdle()

        assertThat(fakeDirectionsService.requestedCount).isEqualTo(1)
    }

    @Test
    fun recalculation_throttlesRepeatedCallsWithinCooldown() = runTest(testDispatcher) {
        var fakeCurrentTime = 1000L
        sessionManager.timeProvider = { fakeCurrentTime }
        sessionManager.recalculationCooldownMs = 10_000L

        sessionManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi")
        advanceUntilIdle()

        val farOffRouteLoc = LatLng(21.0010, 105.0025)
        sessionManager.onLocationUpdate(farOffRouteLoc)
        advanceUntilIdle()
        assertThat(fakeDirectionsService.requestedCount).isEqualTo(1)

        // 3 seconds later (within 10s cooldown): another off-route fix should NOT trigger API
        fakeCurrentTime += 3000L
        sessionManager.onLocationUpdate(farOffRouteLoc)
        advanceUntilIdle()
        assertThat(fakeDirectionsService.requestedCount).isEqualTo(1)

        // 11 seconds later (past cooldown): off-route fix triggers again
        fakeCurrentTime += 8000L
        sessionManager.onLocationUpdate(farOffRouteLoc)
        advanceUntilIdle()
        assertThat(fakeDirectionsService.requestedCount).isEqualTo(2)
    }

    @Test
    fun recalculationFailure_retainsSessionStateAndSetsError() = runTest(testDispatcher) {
        fakeDirectionsService.resultToReturn = Result.failure(Exception("Network timeout"))

        sessionManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi")
        advanceUntilIdle()

        val farOffRouteLoc = LatLng(21.0010, 105.0025)
        sessionManager.onLocationUpdate(farOffRouteLoc)
        advanceUntilIdle()

        assertThat(sessionManager.isNavigating.value).isTrue()
        assertThat(sessionManager.recalculationError.value).isEqualTo("Network timeout")
        assertThat(sessionManager.activeRoute.value).isEqualTo(sampleRoute)
    }

    @Test
    fun recalculationThrowsException_resetsIsRecalculatingAndPublishesError() = runTest(testDispatcher) {
        val throwingDirectionsService = object : DirectionsService {
            override suspend fun getDirections(origin: LatLng, destination: LatLng, mode: TravelMode): Result<NavRoute> {
                throw RuntimeException("Unexpected service crash")
            }

            override suspend fun getDirectionsByPlaceId(origin: LatLng, destinationPlaceId: String, mode: TravelMode): Result<NavRoute> =
                getDirections(origin, LatLng(0.0, 0.0), mode)
        }

        sessionManager.setDirectionsService(throwingDirectionsService)
        sessionManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi")
        advanceUntilIdle()

        val farOffRouteLoc = LatLng(21.0010, 105.0025)
        sessionManager.onLocationUpdate(farOffRouteLoc)
        advanceUntilIdle()

        assertThat(sessionManager.isRecalculating.value).isFalse()
        assertThat(sessionManager.recalculationError.value).isEqualTo("Unexpected service crash")
        assertThat(sessionManager.isNavigating.value).isTrue()
    }

    @Test
    fun recalculationFromOldSession_delayedResponse_doesNotOverwriteNewSessionRoute() = runTest(testDispatcher) {
        val delayDeferred = kotlinx.coroutines.CompletableDeferred<NavRoute>()
        val delayedDirectionsService = object : DirectionsService {
            override suspend fun getDirections(origin: LatLng, destination: LatLng, mode: TravelMode): Result<NavRoute> {
                return try {
                    val route = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        delayDeferred.await()
                    }
                    Result.success(route)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            override suspend fun getDirectionsByPlaceId(origin: LatLng, destinationPlaceId: String, mode: TravelMode): Result<NavRoute> =
                getDirections(origin, LatLng(0.0, 0.0), mode)
        }

        sessionManager.setDirectionsService(delayedDirectionsService)

        // Start session 1
        sessionManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi")
        advanceUntilIdle()

        // Trigger recalculation in session 1 (large deviation)
        val farOffRouteLoc = LatLng(21.0010, 105.0025)
        sessionManager.onLocationUpdate(farOffRouteLoc)
        // Allow coroutine to start and enter getDirections awaiting delayDeferred
        testScheduler.runCurrent()
        assertThat(sessionManager.isRecalculating.value).isTrue()
        // Start session 2 with a new distinct route
        val stepNew = NavStep("Session 2 Step", "Session 2 Street", ManeuverType.STRAIGHT, 500, 100, LatLng(21.050, 105.800), LatLng(21.055, 105.800), emptyList())
        val session2Route = NavRoute(
            origin = LatLng(21.050, 105.800),
            destination = LatLng(21.055, 105.800),
            destinationAddress = "Session 2 Destination",
            totalDistanceMeters = 500,
            totalDurationSeconds = 100,
            travelMode = TravelMode.DRIVING,
            overviewPolyline = emptyList(),
            steps = listOf(stepNew)
        )
        sessionManager.startSession(session2Route, TravelMode.DRIVING, "Session 2 Destination")
        advanceUntilIdle()

        assertThat(sessionManager.activeRoute.value).isEqualTo(session2Route)

        // Now session 1's recalculation completes
        val staleRecalculatedRoute = NavRoute(
            origin = farOffRouteLoc,
            destination = sampleRoute.destination,
            destinationAddress = "Stale Route",
            totalDistanceMeters = 999,
            totalDurationSeconds = 999,
            travelMode = TravelMode.DRIVING,
            overviewPolyline = emptyList(),
            steps = listOf(NavStep("Stale Step", "Stale St", ManeuverType.TURN_LEFT, 999, 999, farOffRouteLoc, sampleRoute.destination!!, emptyList()))
        )
        delayDeferred.complete(staleRecalculatedRoute)
        advanceUntilIdle()

        // Verify session 2's route and progress are preserved and NOT overwritten by stale result
        assertThat(sessionManager.activeRoute.value).isEqualTo(session2Route)
        assertThat(sessionManager.navigationProgress.value?.currentStep?.streetName).isEqualTo("Session 2 Street")
        val lastWatchMsg = fakeWearEngineService.sentMessages.last()
        assertThat(lastWatchMsg.street).isEqualTo("Session 2 Street")
    }

    private class FakeWearEngineService : WearEngineService {
        private val _connectionState = MutableStateFlow<WatchConnectionState>(WatchConnectionState.Connected("GT 5", "GT5"))
        override val connectionState: StateFlow<WatchConnectionState> = _connectionState.asStateFlow()

        private val _isReconnecting = MutableStateFlow(false)
        override val isReconnecting: StateFlow<Boolean> = _isReconnecting.asStateFlow()

        val sentMessages = mutableListOf<WatchNavMessage>()
        var sendResultToReturn: Result<Unit> = Result.success(Unit)
        var autoReconnectStarted = false
        var autoReconnectStopped = false
        var onReconnectedCallback: (suspend () -> Unit)? = null

        override suspend fun checkPermissions(): Boolean = true
        override suspend fun requestPermission(): Boolean = true
        override suspend fun checkConnection(): WatchConnectionState = _connectionState.value

        override suspend fun sendNavMessage(message: WatchNavMessage): Result<Unit> {
            sentMessages.add(message)
            return sendResultToReturn
        }

        override suspend fun pingWatch(): Result<Boolean> = Result.success(true)

        override fun startAutoReconnect(onReconnected: (suspend () -> Unit)?) {
            autoReconnectStarted = true
            autoReconnectStopped = false
            _isReconnecting.value = true
            this.onReconnectedCallback = onReconnected
        }

        override fun stopAutoReconnect() {
            autoReconnectStopped = true
            autoReconnectStarted = false
            _isReconnecting.value = false
            onReconnectedCallback = null
        }

        override fun release() {
            stopAutoReconnect()
        }
    }

    private class FakeDirectionsService : DirectionsService {
        var requestedCount = 0
        var routeToReturn: NavRoute? = null
        var resultToReturn: Result<NavRoute>? = null

        override suspend fun getDirections(
            origin: LatLng,
            destination: LatLng,
            mode: TravelMode
        ): Result<NavRoute> {
            requestedCount++
            val custom = resultToReturn
            if (custom != null) return custom
            val route = routeToReturn ?: return Result.failure(IllegalStateException("No route set"))
            return Result.success(route)
        }

        override suspend fun getDirectionsByPlaceId(
            origin: LatLng,
            destinationPlaceId: String,
            mode: TravelMode
        ): Result<NavRoute> = getDirections(origin, LatLng(0.0, 0.0), mode)
    }
}
