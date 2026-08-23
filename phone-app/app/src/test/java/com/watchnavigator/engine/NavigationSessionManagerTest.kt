package com.watchnavigator.engine

import com.google.common.truth.Truth.assertThat
import com.watchnavigator.data.WearEngineService
import com.watchnavigator.model.LatLng
import com.watchnavigator.model.ManeuverType
import com.watchnavigator.model.NavRoute
import com.watchnavigator.model.NavStep
import com.watchnavigator.model.TravelMode
import com.watchnavigator.model.WatchConnectionState
import com.watchnavigator.model.WatchNavMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private lateinit var sessionManager: NavigationSessionManager
    private lateinit var sampleRoute: NavRoute

    private val p0 = LatLng(21.0000, 105.0000)
    private val p1 = LatLng(21.0010, 105.0000)
    private val p2 = LatLng(21.0020, 105.0000)
    private val p3 = LatLng(21.0020, 105.0040)

    @Before
    fun setUp() {
        fakeWearEngineService = FakeWearEngineService()
        sessionManager = NavigationSessionManager(fakeWearEngineService, testDispatcher)

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
    fun sendError_capturesWatchSendFailure() = runTest(testDispatcher) {
        fakeWearEngineService.sendResultToReturn = Result.failure(Exception("Bluetooth lost"))

        sessionManager.startSession(sampleRoute, TravelMode.DRIVING, "Target Hanoi")
        advanceUntilIdle()

        assertThat(sessionManager.watchSendError.value).isEqualTo("Bluetooth lost")
    }

    private class FakeWearEngineService : WearEngineService {
        private val _connectionState = MutableStateFlow<WatchConnectionState>(WatchConnectionState.Connected("GT 5", "GT5"))
        override val connectionState: StateFlow<WatchConnectionState> = _connectionState.asStateFlow()

        val sentMessages = mutableListOf<WatchNavMessage>()
        var sendResultToReturn: Result<Unit> = Result.success(Unit)

        override suspend fun checkPermissions(): Boolean = true
        override suspend fun checkConnection(): WatchConnectionState = _connectionState.value

        override suspend fun sendNavMessage(message: WatchNavMessage): Result<Unit> {
            sentMessages.add(message)
            return sendResultToReturn
        }

        override suspend fun pingWatch(): Result<Boolean> = Result.success(true)
        override fun release() {}
    }
}
