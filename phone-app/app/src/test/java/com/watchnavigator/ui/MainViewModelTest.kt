package com.watchnavigator.ui

import com.google.common.truth.Truth.assertThat
import com.watchnavigator.data.DirectionsService
import com.watchnavigator.data.PlacesSearchService
import com.watchnavigator.model.LatLng
import com.watchnavigator.model.ManeuverType
import com.watchnavigator.model.NavRoute
import com.watchnavigator.model.NavStep
import com.watchnavigator.model.PlaceSuggestion
import com.watchnavigator.model.TravelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakePlacesService = FakePlacesSearchService()
        fakeDirectionsService = FakeDirectionsService()
        viewModel = MainViewModel(fakePlacesService, fakeDirectionsService)
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

        assertThat(viewModel.selectedDestination.value).isEqualTo(suggestion)
        assertThat(viewModel.destinationLatLng.value).isEqualTo(destinationLocation)
        assertThat(viewModel.routeState.value).isInstanceOf(RouteUiState.Success::class.java)
        val routeSuccess = viewModel.routeState.value as RouteUiState.Success
        assertThat(routeSuccess.route.totalDistanceMeters).isEqualTo(5000)
    }

    @Test
    fun travelModeChange_triggersRouteRecalculation() = runTest {
        viewModel.setCurrentLocation(LatLng(21.0285, 105.8542))
        val suggestion = PlaceSuggestion("p1", "Landmark 72", "Hanoi", "Landmark 72, Hanoi")
        fakePlacesService.placeLocationToReturn = LatLng(21.0175, 105.7842)

        viewModel.selectSuggestion(suggestion)
        advanceUntilIdle()

        assertThat(fakeDirectionsService.lastRequestedMode).isEqualTo(TravelMode.DRIVING)

        viewModel.setTravelMode(TravelMode.WALKING)
        advanceUntilIdle()

        assertThat(viewModel.travelMode.value).isEqualTo(TravelMode.WALKING)
        assertThat(fakeDirectionsService.lastRequestedMode).isEqualTo(TravelMode.WALKING)
    }

    @Test
    fun routeCalculationError_updatesRouteStateToError() = runTest {
        viewModel.setCurrentLocation(LatLng(21.0285, 105.8542))
        val suggestion = PlaceSuggestion("p1", "Landmark 72", "Hanoi", "Landmark 72, Hanoi")
        fakePlacesService.placeLocationToReturn = LatLng(21.0175, 105.7842)
        fakeDirectionsService.errorToThrow = RuntimeException("Network connection failed")

        viewModel.selectSuggestion(suggestion)
        advanceUntilIdle()

        assertThat(viewModel.routeState.value).isInstanceOf(RouteUiState.Error::class.java)
        val errorState = viewModel.routeState.value as RouteUiState.Error
        assertThat(errorState.message).contains("Network connection failed")
    }

    @Test
    fun selectSuggestion_resetsDestinationLatLngImmediately() = runTest {
        viewModel.setCurrentLocation(LatLng(21.0285, 105.8542))
        val suggestionA = PlaceSuggestion("p1", "Landmark 72", "Hanoi", "Landmark 72, Hanoi")
        fakePlacesService.placeLocationToReturn = LatLng(21.0175, 105.7842)

        viewModel.selectSuggestion(suggestionA)
        advanceUntilIdle()
        assertThat(viewModel.destinationLatLng.value).isEqualTo(LatLng(21.0175, 105.7842))

        val suggestionB = PlaceSuggestion("p2", "Keangnam", "Hanoi", "Keangnam, Hanoi")
        viewModel.selectSuggestion(suggestionB)
        // Immediately after selecting B, destinationLatLng should be null (not A's coords)
        assertThat(viewModel.destinationLatLng.value).isNull()
    }

    private class FakePlacesSearchService : PlacesSearchService {
        var suggestionsToReturn: List<PlaceSuggestion> = emptyList()
        var placeLocationToReturn: LatLng = LatLng(0.0, 0.0)
        var errorToThrow: Exception? = null

        override suspend fun searchSuggestions(query: String): Result<List<PlaceSuggestion>> {
            errorToThrow?.let { return Result.failure(it) }
            return Result.success(suggestionsToReturn)
        }

        override suspend fun fetchPlaceLocation(placeId: String): Result<LatLng> {
            errorToThrow?.let { return Result.failure(it) }
            return Result.success(placeLocationToReturn)
        }

        override fun createNewSessionToken() {}
    }

    private class FakeDirectionsService : DirectionsService {
        var routeToReturn: NavRoute? = null
        var errorToThrow: Exception? = null
        var lastRequestedMode: TravelMode? = null
        var lastRequestedDestinationLocation: LatLng? = null
        var lastRequestedDestinationPlaceId: String? = null

        override suspend fun getDirections(
            origin: LatLng,
            destination: LatLng,
            mode: TravelMode
        ): Result<NavRoute> {
            lastRequestedMode = mode
            lastRequestedDestinationLocation = destination
            errorToThrow?.let { return Result.failure(it) }
            return Result.success(
                routeToReturn ?: NavRoute(
                    origin,
                    destination,
                    "Destination",
                    1000,
                    100,
                    mode,
                    emptyList(),
                    emptyList()
                )
            )
        }

        override suspend fun getDirectionsByPlaceId(
            origin: LatLng,
            destinationPlaceId: String,
            mode: TravelMode
        ): Result<NavRoute> {
            lastRequestedMode = mode
            lastRequestedDestinationPlaceId = destinationPlaceId
            return getDirections(origin, LatLng(0.0, 0.0), mode)
        }
    }
}
