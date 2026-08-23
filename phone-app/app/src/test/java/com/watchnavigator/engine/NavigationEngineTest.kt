package com.watchnavigator.engine

import com.google.common.truth.Truth.assertThat
import com.watchnavigator.model.LatLng
import com.watchnavigator.model.ManeuverType
import com.watchnavigator.model.NavRoute
import com.watchnavigator.model.NavStep
import com.watchnavigator.model.TravelMode
import org.junit.Before
import org.junit.Test

class NavigationEngineTest {

    private lateinit var sampleRoute: NavRoute

    // Route coordinates:
    // Step 0: (21.0000, 105.0000) -> (21.0020, 105.0000) (Head north 222m along Street A)
    // Step 1: (21.0020, 105.0000) -> (21.0020, 105.0040) (Turn right onto Street B, east 415m)
    // Destination: (21.0020, 105.0040)
    private val p0 = LatLng(21.0000, 105.0000)
    private val p1 = LatLng(21.0010, 105.0000)
    private val p2 = LatLng(21.0020, 105.0000)
    private val p3 = LatLng(21.0020, 105.0020)
    private val p4 = LatLng(21.0020, 105.0040)

    @Before
    fun setUp() {
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
            endLocation = p4,
            polylinePoints = listOf(p2, p3, p4)
        )

        sampleRoute = NavRoute(
            origin = p0,
            destination = p4,
            destinationAddress = "Hanoi Destination",
            totalDistanceMeters = 637,
            totalDurationSeconds = 150,
            travelMode = TravelMode.DRIVING,
            overviewPolyline = listOf(p0, p1, p2, p3, p4),
            steps = listOf(step0, step1)
        )
    }

    @Test
    fun processLocation_atStart_returnsFirstStepWithFullDistance() {
        val engine = NavigationEngine(sampleRoute)
        val progress = engine.processLocation(p0)

        assertThat(progress.currentStepIndex).isEqualTo(0)
        assertThat(progress.currentStep.streetName).isEqualTo("Street A")
        assertThat(progress.isArrived).isFalse()
        assertThat(progress.isOffRoute).isFalse()
        assertThat(progress.remainingDistanceToNextTurnMeters).isGreaterThan(200)
        assertThat(progress.totalRemainingDistanceMeters).isGreaterThan(600)
    }

    @Test
    fun processLocation_midwayStep0_updatesRemainingDistance() {
        val engine = NavigationEngine(sampleRoute)
        val progress = engine.processLocation(p1)

        assertThat(progress.currentStepIndex).isEqualTo(0)
        assertThat(progress.remainingDistanceToNextTurnMeters).isGreaterThan(95)
        assertThat(progress.remainingDistanceToNextTurnMeters).isLessThan(125)
        assertThat(progress.totalRemainingDistanceMeters).isGreaterThan(500)
        assertThat(progress.isArrived).isFalse()
    }

    @Test
    fun processLocation_approachingStep1_advancesStepIndex() {
        val engine = NavigationEngine(sampleRoute)

        // First at p0
        engine.processLocation(p0)

        // Now move near p2 (the turn intersection)
        val progressAtTurn = engine.processLocation(p2)
        assertThat(progressAtTurn.currentStepIndex).isEqualTo(1)
        assertThat(progressAtTurn.currentStep.maneuver).isEqualTo(ManeuverType.TURN_RIGHT)
        assertThat(progressAtTurn.currentStep.streetName).isEqualTo("Street B")
        assertThat(progressAtTurn.isArrived).isFalse()
    }

    @Test
    fun processLocation_movingAlongStep1_computesStep1Distance() {
        val engine = NavigationEngine(sampleRoute)

        // Start and advance
        engine.processLocation(p0)
        engine.processLocation(p2)

        // Move to p3 (midway on step 1)
        val progress = engine.processLocation(p3)
        assertThat(progress.currentStepIndex).isEqualTo(1)
        assertThat(progress.remainingDistanceToNextTurnMeters).isGreaterThan(180)
        assertThat(progress.remainingDistanceToNextTurnMeters).isLessThan(225)
        assertThat(progress.isArrived).isFalse()
    }

    @Test
    fun processLocation_atDestination_marksArrival() {
        val engine = NavigationEngine(sampleRoute)

        engine.processLocation(p0)
        engine.processLocation(p2)

        // Now at destination p4
        val progress = engine.processLocation(p4)
        assertThat(progress.isArrived).isTrue()
        assertThat(progress.remainingDistanceToNextTurnMeters).isEqualTo(0)
        assertThat(progress.totalRemainingDistanceMeters).isEqualTo(0)
        assertThat(engine.isArrived).isTrue()

        // Subsequent location calls stay arrived
        val progressAfter = engine.processLocation(p4)
        assertThat(progressAfter.isArrived).isTrue()
    }

    @Test
    fun processLocation_offRoute_detectsDeviation() {
        val engine = NavigationEngine(sampleRoute)

        // Point 100 meters east of p0 (away from Street A polyline)
        val deviatedPoint = LatLng(21.0000, 105.0010)
        val progress = engine.processLocation(deviatedPoint)

        assertThat(progress.isOffRoute).isTrue()
        assertThat(progress.offRouteDistanceMeters).isGreaterThan(50.0)
    }

    @Test
    fun reset_resetsIndexAndArrival() {
        val engine = NavigationEngine(sampleRoute)
        engine.processLocation(p4) // Arrived
        assertThat(engine.isArrived).isTrue()

        engine.reset()
        assertThat(engine.isArrived).isFalse()
        assertThat(engine.currentStepIndex).isEqualTo(0)
    }

    @Test
    fun processLocation_emptySteps_handlesGracefullyAsArrived() {
        val emptyRoute = sampleRoute.copy(steps = emptyList())
        val engine = NavigationEngine(emptyRoute)
        val progress = engine.processLocation(p0)

        assertThat(progress.isArrived).isTrue()
        assertThat(progress.remainingDistanceToNextTurnMeters).isEqualTo(0)
    }
}
