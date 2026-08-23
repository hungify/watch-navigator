package com.watchnavigator.ui

import com.google.common.truth.Truth.assertThat
import com.watchnavigator.data.DirectionsService
import com.watchnavigator.data.PlacesSearchService
import com.watchnavigator.data.WearEngineService
import com.watchnavigator.engine.NavigationSessionManager
import com.watchnavigator.model.LatLng
import com.watchnavigator.model.ManeuverType
import com.watchnavigator.model.NavRoute
import com.watchnavigator.model.NavStep
import com.watchnavigator.model.PlaceSuggestion
import com.watchnavigator.model.TravelMode
import com.watchnavigator.model.WatchConnectionState
import com.watchnavigator.model.WatchNavMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakePlacesService: FakePlacesSearchService
    private lateinit var fakeDirectionsService: FakeDirectionsService
    private lateinit var fakeWearEngineService: FakeWearEngineService
    private lateinit var sessionManager: NavigationSessionManager
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakePlacesService = FakePlacesSearchService()
        fakeDirectionsService = FakeDirectionsService()
        fakeWearEngineService = FakeWearEngineService()
        sessionManager = NavigationSessionManager(fakeWearEngineService, testDispatcher)
        viewModel = MainViewModel(fakePlacesService, fakeDirectionsService, fakeWearEngineService, sessionManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun queryChanged_withDebounce_triggersSearchAndUpdatesSuggestions() = runTest {
        fakePlacesService.suggestionsToReturn = listOf(
            PlaceSuggestion("id1", "Keangnam Tower", "Nam Tu Liem, Hanoi", "Keangnam Tower, Nam Tu Liem, Hanoi")
        )

        viewModel.onQueryChanged("Keangnam")
        advanceTimeBy(350)
        advanceUntilIdle()

        val state = viewModel.suggestionsState.value
        assertThat(state).isInstanceOf(SuggestionsUiState.Success::class.java)
        val successState = state as SuggestionsUiState.Success
        assertThat(successState.suggestions).hasSize(1)
        assertThat(successState.suggestions[0].primaryText).isEqualTo("Keangnam Tower")
    }

    @Test
    fun selectSuggestion_fetchesLocationAndCalculatesRoute() = runTest {
        viewModel.setCurrentLocation(LatLng(21.0285, 105.8542))

        val suggestion = PlaceSuggestion("p1", "Landmark 72", "Hanoi", "Landmark 72, Hanoi")
        val destinationLocation = LatLng(21.0175, 105.7842)
        fakePlacesService.placeLocationToReturn = destinationLocation

        val sampleRoute = NavRoute(
            origin = LatLng(21.0285, 105.8542),
            destination = destinationLocation,
            destinationAddress = "Landmark 72, Hanoi",
            totalDistanceMeters = 5000,
            totalDurationSeconds = 900,
            travelMode = TravelMode.DRIVING,
            overviewPolyline = listOf(LatLng(21.0285, 105.8542), destinationLocation),
            steps = listOf(
                NavStep(
                    instruction = "Head west",
                    streetName = "Nguyen Trai",
                    maneuver = ManeuverType.STRAIGHT,
                    distanceMeters = 5000,
                    durationSeconds = 900,
                    startLocation = LatLng(21.0285, 105.8542),
                    endLocation = destinationLocation,
                    polylinePoints = emptyList()
                )
            )
        )
        fakeDirectionsService.routeToReturn = sampleRoute

        viewModel.selectSuggestion(suggestion)
        advanceUntilIdle()

        val routeState = viewModel.routeState.value
        assertThat(routeState).isInstanceOf(RouteUiState.Success::class.java)
        val routeSuccess = routeState as RouteUiState.Success
        assertThat(routeSuccess.route.totalDistanceMeters).isEqualTo(5000)
    }

    @Test
    fun travelModeChange_triggersRouteRecalculation() = runTest {
        viewModel.setCurrentLocation(LatLng(21.0285, 105.8542))
        val suggestion = PlaceSuggestion("p1", "Landmark 72", "Hanoi", "Landmark 72, Hanoi")
        val destinationLocation = LatLng(21.0175, 105.7842)
        fakePlacesService.placeLocationToReturn = destinationLocation
        fakeDirectionsService.routeToReturn = createSampleRoute(destinationLocation)

        viewModel.selectSuggestion(suggestion)
        advanceUntilIdle()

        viewModel.setTravelMode(TravelMode.WALKING)
        advanceUntilIdle()

        assertThat(fakeDirectionsService.lastRequestedMode).isEqualTo(TravelMode.WALKING)
    }

    @Test
    fun routeCalculationError_updatesRouteStateToError() = runTest {
        viewModel.setCurrentLocation(LatLng(21.0285, 105.8542))
        val suggestion = PlaceSuggestion("p1", "Unknown Place", "Hanoi", "Unknown Place, Hanoi")
        val destinationLocation = LatLng(21.0175, 105.7842)
        fakePlacesService.placeLocationToReturn = destinationLocation
        fakeDirectionsService.exceptionToThrow = RuntimeException("Route not found")

        viewModel.selectSuggestion(suggestion)
        advanceUntilIdle()

        val routeState = viewModel.routeState.value
        assertThat(routeState).isInstanceOf(RouteUiState.Error::class.java)
        assertThat((routeState as RouteUiState.Error).message).contains("Route not found")
    }

    @Test
    fun selectSuggestion_resetsDestinationLatLngImmediately() = runTest {
        val suggestion = PlaceSuggestion("p1", "Landmark 72", "Hanoi", "Landmark 72, Hanoi")
        viewModel.selectSuggestion(suggestion)

        assertThat(viewModel.selectedDestination.value).isEqualTo(suggestion)
        assertThat(viewModel.destinationLatLng.value).isNull()
        assertThat(viewModel.suggestionsState.value).isEqualTo(SuggestionsUiState.Idle)
    }

    @Test
    fun watchConnectionState_reflectsWearEngineServiceState() = runTest {
        fakeWearEngineService.connectionStateToReturn = WatchConnectionState.Connected("HUAWEI WATCH GT 5", "GT5-PRO")
        viewModel.checkWatchConnection()
        advanceUntilIdle()

        val state = viewModel.watchConnectionState.value
        assertThat(state).isInstanceOf(WatchConnectionState.Connected::class.java)
        assertThat((state as WatchConnectionState.Connected).deviceName).isEqualTo("HUAWEI WATCH GT 5")
    }

    @Test
    fun startNavigation_dispatchesFirstTurnMessageToWatch() = runTest {
        viewModel.setCurrentLocation(LatLng(21.0285, 105.8542))
        val dest = LatLng(21.0175, 105.7842)
        val route = createSampleRoute(dest)
        fakeDirectionsService.routeToReturn = route
        fakePlacesService.placeLocationToReturn = dest

        viewModel.selectSuggestion(PlaceSuggestion("p1", "Landmark 72", "Hanoi", "Landmark 72, Hanoi"))
        advanceUntilIdle()

        viewModel.startNavigation()
        advanceUntilIdle()

        assertThat(viewModel.isNavigating.value).isTrue()
        assertThat(viewModel.currentStepIndex.value).isEqualTo(0)
        assertThat(fakeWearEngineService.sentMessages).hasSize(1)
        val sent = fakeWearEngineService.sentMessages[0]
        assertThat(sent.turn).isEqualTo("straight")
        assertThat(sent.distanceMeters).isEqualTo(5000)
        assertThat(sent.street).isEqualTo("Nguyen Trai")
        assertThat(viewModel.lastSentWatchMessage.value).isEqualTo(sent)
    }

    @Test
    fun updateNavigationStep_sendsUpdatedMessageToWatch() = runTest {
        viewModel.setCurrentLocation(LatLng(21.0285, 105.8542))
        val dest = LatLng(21.0175, 105.7842)
        val route = NavRoute(
            origin = LatLng(21.0285, 105.8542),
            destination = dest,
            destinationAddress = "Landmark 72, Hanoi",
            totalDistanceMeters = 5000,
            totalDurationSeconds = 900,
            travelMode = TravelMode.DRIVING,
            overviewPolyline = listOf(LatLng(21.0285, 105.8542), dest),
            steps = listOf(
                NavStep("Head straight", "Nguyen Trai", ManeuverType.STRAIGHT, 2000, 300, LatLng(21.0, 105.8), LatLng(21.01, 105.8), emptyList()),
                NavStep("Turn left onto Tran Phu", "Tran Phu", ManeuverType.TURN_LEFT, 3000, 600, LatLng(21.01, 105.8), dest, emptyList())
            )
        )
        fakeDirectionsService.routeToReturn = route
        fakePlacesService.placeLocationToReturn = dest

        viewModel.selectSuggestion(PlaceSuggestion("p1", "Landmark 72", "Hanoi", "Landmark 72, Hanoi"))
        advanceUntilIdle()

        viewModel.startNavigation()
        advanceUntilIdle()

        // Advance to step 1 with 150m remaining
        viewModel.updateNavigationStep(1, remainingDistanceMeters = 150)
        advanceUntilIdle()

        assertThat(viewModel.currentStepIndex.value).isEqualTo(1)
        assertThat(fakeWearEngineService.sentMessages).hasSize(2)
        val secondMsg = fakeWearEngineService.sentMessages[1]
        assertThat(secondMsg.turn).isEqualTo("left")
        assertThat(secondMsg.distanceMeters).isEqualTo(150)
        assertThat(secondMsg.street).isEqualTo("Tran Phu")
    }

    @Test
    fun stopNavigation_resetsNavigatingStateAndSendsStopMessage() = runTest {
        viewModel.setCurrentLocation(LatLng(21.0285, 105.8542))
        val dest = LatLng(21.0175, 105.7842)
        fakeDirectionsService.routeToReturn = createSampleRoute(dest)
        fakePlacesService.placeLocationToReturn = dest

        viewModel.selectSuggestion(PlaceSuggestion("p1", "Landmark 72", "Hanoi", "Landmark 72, Hanoi"))
        advanceUntilIdle()

        viewModel.startNavigation()
        assertThat(viewModel.isNavigating.value).isTrue()

        viewModel.stopNavigation()
        advanceUntilIdle()

        assertThat(viewModel.isNavigating.value).isFalse()
        assertThat(viewModel.currentStepIndex.value).isEqualTo(0)
        val lastMsg = fakeWearEngineService.sentMessages.lastOrNull()
        assertThat(lastMsg).isNotNull()
        assertThat(lastMsg?.turn).isEqualTo(WatchNavMessage.TERMINAL_TURN)
    }

    @Test
    fun sendWatchMessage_whenSendFails_updatesWatchSendError() = runTest {
        fakeWearEngineService.sendResultToReturn = Result.failure(RuntimeException("Transmission failed"))
        viewModel.setCurrentLocation(LatLng(21.0285, 105.8542))
        val dest = LatLng(21.0175, 105.7842)
        fakeDirectionsService.routeToReturn = createSampleRoute(dest)
        fakePlacesService.placeLocationToReturn = dest

        viewModel.selectSuggestion(PlaceSuggestion("p1", "Landmark 72", "Hanoi", "Landmark 72, Hanoi"))
        advanceUntilIdle()

        viewModel.startNavigation()
        advanceUntilIdle()

        assertThat(viewModel.watchSendError.value).isEqualTo("Transmission failed")
        assertThat(viewModel.lastSentWatchMessage.value).isNull()
    }


    @Test
    fun checkWatchConnection_coalescesConcurrentChecks() = runTest {
        val initialCount = fakeWearEngineService.checkConnectionCount
        viewModel.checkWatchConnection()
        viewModel.checkWatchConnection()
        advanceUntilIdle()

        assertThat(fakeWearEngineService.checkConnectionCount).isEqualTo(initialCount + 1)
    }
    @Test
    fun sendArrivalToWatch_sendsArrivalPayload() = runTest {
        viewModel.setCurrentLocation(LatLng(21.0285, 105.8542))
        val dest = LatLng(21.0175, 105.7842)
        fakeDirectionsService.routeToReturn = createSampleRoute(dest)
        fakePlacesService.placeLocationToReturn = dest

        viewModel.selectSuggestion(PlaceSuggestion("p1", "Landmark 72", "Hanoi", "Landmark 72, Hanoi"))
        advanceUntilIdle()

        viewModel.sendArrivalToWatch()
        advanceUntilIdle()

        val lastMsg = fakeWearEngineService.sentMessages.lastOrNull()
        assertThat(lastMsg).isNotNull()
        assertThat(lastMsg?.turn).isEqualTo("arrive")
        assertThat(lastMsg?.distanceMeters).isEqualTo(0)
        assertThat(lastMsg?.street).isEqualTo("Landmark 72")
    }

    private fun createSampleRoute(destinationLocation: LatLng): NavRoute {
        return NavRoute(
            origin = LatLng(21.0285, 105.8542),
            destination = destinationLocation,
            destinationAddress = "Landmark 72, Hanoi",
            totalDistanceMeters = 5000,
            totalDurationSeconds = 900,
            travelMode = TravelMode.DRIVING,
            overviewPolyline = listOf(LatLng(21.0285, 105.8542), destinationLocation),
            steps = listOf(
                NavStep(
                    instruction = "Head west",
                    streetName = "Nguyen Trai",
                    maneuver = ManeuverType.STRAIGHT,
                    distanceMeters = 5000,
                    durationSeconds = 900,
                    startLocation = LatLng(21.0285, 105.8542),
                    endLocation = destinationLocation,
                    polylinePoints = emptyList()
                )
            )
        )
    }

    private class FakePlacesSearchService : PlacesSearchService {
        var suggestionsToReturn: List<PlaceSuggestion> = emptyList()
        var placeLocationToReturn: LatLng? = null
        var exceptionToThrow: Exception? = null

        override suspend fun searchSuggestions(query: String): Result<List<PlaceSuggestion>> {
            exceptionToThrow?.let { return Result.failure(it) }
            return Result.success(suggestionsToReturn)
        }

        override suspend fun fetchPlaceLocation(placeId: String): Result<LatLng> {
            exceptionToThrow?.let { return Result.failure(it) }
            val loc = placeLocationToReturn ?: return Result.failure(IllegalStateException("No location"))
            return Result.success(loc)
        }

        override fun createNewSessionToken() {}
    }

    private class FakeDirectionsService : DirectionsService {
        var routeToReturn: NavRoute? = null
        var exceptionToThrow: Exception? = null
        var lastRequestedMode: TravelMode? = null

        override suspend fun getDirections(
            origin: LatLng,
            destination: LatLng,
            mode: TravelMode
        ): Result<NavRoute> {
            lastRequestedMode = mode
            exceptionToThrow?.let { return Result.failure(it) }
            val route = routeToReturn ?: return Result.failure(IllegalStateException("No route"))
            return Result.success(route)
        }

        override suspend fun getDirectionsByPlaceId(
            origin: LatLng,
            destinationPlaceId: String,
            mode: TravelMode
        ): Result<NavRoute> {
            lastRequestedMode = mode
            exceptionToThrow?.let { return Result.failure(it) }
            val route = routeToReturn ?: return Result.failure(IllegalStateException("No route"))
            return Result.success(route)
        }
    }

    private class FakeWearEngineService : WearEngineService {
        val _connectionState = MutableStateFlow<WatchConnectionState>(WatchConnectionState.Disconnected())
        override val connectionState: StateFlow<WatchConnectionState> = _connectionState.asStateFlow()

        var permissionsGranted = true
        var connectionStateToReturn: WatchConnectionState = WatchConnectionState.Connected("HUAWEI WATCH GT 5", "GT5-PRO")
        val sentMessages = mutableListOf<WatchNavMessage>()
        var sendResultToReturn: Result<Unit> = Result.success(Unit)
        var pingResultToReturn: Result<Boolean> = Result.success(true)

        var checkConnectionCount = 0

        override suspend fun checkPermissions(): Boolean = permissionsGranted

        override suspend fun checkConnection(): WatchConnectionState {
            checkConnectionCount++
            _connectionState.value = connectionStateToReturn
            return connectionStateToReturn
        }
        override suspend fun sendNavMessage(message: WatchNavMessage): Result<Unit> {
            sentMessages.add(message)
            return sendResultToReturn
        }

        override suspend fun pingWatch(): Result<Boolean> = pingResultToReturn

        override fun release() {
            _connectionState.value = WatchConnectionState.Disconnected()
        }
    }
}
