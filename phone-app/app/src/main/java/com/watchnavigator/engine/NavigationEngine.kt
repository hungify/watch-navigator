package com.watchnavigator.engine

import com.watchnavigator.model.NavRoute
import com.watchnavigator.model.NavStep
import com.watchnavigator.model.LatLng
import com.watchnavigator.util.GeoUtils

data class NavigationProgress(
    val currentStepIndex: Int,
    val currentStep: NavStep,
    val remainingDistanceToNextTurnMeters: Int,
    val totalRemainingDistanceMeters: Int,
    val isArrived: Boolean = false,
    val isOffRoute: Boolean = false,
    val offRouteDistanceMeters: Double = 0.0
)

class NavigationEngine(
    val route: NavRoute,
    val stepAdvanceThresholdMeters: Double = 25.0,
    val arrivalThresholdMeters: Double = 25.0,
    val offRouteThresholdMeters: Double = 50.0
) {
    private var _currentStepIndex: Int = 0
    val currentStepIndex: Int
        get() = _currentStepIndex

    private var _isArrived: Boolean = false
    val isArrived: Boolean
        get() = _isArrived

    /**
     * Processes a new user GPS coordinate and returns the updated navigation progress.
     */
    fun processLocation(location: LatLng): NavigationProgress {
        if (route.steps.isEmpty()) {
            _isArrived = true
            val fallbackStep = NavStep(
                instruction = "Arrived at destination",
                streetName = route.destinationAddress,
                maneuver = com.watchnavigator.model.ManeuverType.ARRIVE,
                distanceMeters = 0,
                durationSeconds = 0,
                startLocation = location,
                endLocation = location,
                polylinePoints = listOf(location)
            )
            return NavigationProgress(
                currentStepIndex = 0,
                currentStep = fallbackStep,
                remainingDistanceToNextTurnMeters = 0,
                totalRemainingDistanceMeters = 0,
                isArrived = true
            )
        }

        if (_isArrived) {
            val lastStep = route.steps.last()
            return NavigationProgress(
                currentStepIndex = route.steps.lastIndex,
                currentStep = lastStep,
                remainingDistanceToNextTurnMeters = 0,
                totalRemainingDistanceMeters = 0,
                isArrived = true
            )
        }

        // 1. Check destination arrival
        val destination = route.destination ?: route.steps.last().endLocation
        val distToDestination = GeoUtils.distanceBetweenMeters(location, destination)
        val isAtLastStep = _currentStepIndex == route.steps.lastIndex

        if (distToDestination <= arrivalThresholdMeters && (isAtLastStep || distToDestination <= 15.0)) {
            _isArrived = true
            _currentStepIndex = route.steps.lastIndex
            return NavigationProgress(
                currentStepIndex = _currentStepIndex,
                currentStep = route.steps.last(),
                remainingDistanceToNextTurnMeters = 0,
                totalRemainingDistanceMeters = 0,
                isArrived = true
            )
        }

        // 2. Evaluate step progression
        evaluateStepProgression(location)

        // 3. Current active step calculations
        val currentStep = route.steps[_currentStepIndex]
        val remainingInStep = GeoUtils.remainingDistanceAlongStep(location, currentStep)

        // 4. Calculate total remaining distance across all subsequent steps
        var totalRemaining = remainingInStep
        for (i in (_currentStepIndex + 1) until route.steps.size) {
            totalRemaining += route.steps[i].distanceMeters
        }

        // 5. Check off-route status
        val distanceToStepPolyline = GeoUtils.distanceToStepPolyline(location, currentStep)
        val isOffRoute = distanceToStepPolyline > offRouteThresholdMeters

        return NavigationProgress(
            currentStepIndex = _currentStepIndex,
            currentStep = currentStep,
            remainingDistanceToNextTurnMeters = remainingInStep,
            totalRemainingDistanceMeters = totalRemaining,
            isArrived = false,
            isOffRoute = isOffRoute,
            offRouteDistanceMeters = distanceToStepPolyline
        )
    }

    private fun evaluateStepProgression(location: LatLng) {
        while (_currentStepIndex < route.steps.lastIndex) {
            val currentStep = route.steps[_currentStepIndex]
            val nextStep = route.steps[_currentStepIndex + 1]

            val distToEndOfCurrent = GeoUtils.distanceBetweenMeters(location, currentStep.endLocation)
            val distToStartOfNext = GeoUtils.distanceBetweenMeters(location, nextStep.startLocation)

            // If user is approaching the transition point
            if (distToEndOfCurrent <= stepAdvanceThresholdMeters || distToStartOfNext <= stepAdvanceThresholdMeters) {
                val currentPolyDist = GeoUtils.distanceToStepPolyline(location, currentStep)
                val nextPolyDist = GeoUtils.distanceToStepPolyline(location, nextStep)

                if (nextPolyDist <= currentPolyDist || distToEndOfCurrent <= 12.0) {
                    _currentStepIndex++
                    continue
                }
            }

            // Look ahead 1 additional step in case of rapid progression or skipped waypoint
            if (_currentStepIndex + 2 < route.steps.size) {
                val stepPlus2 = route.steps[_currentStepIndex + 2]
                val distToStep2Poly = GeoUtils.distanceToStepPolyline(location, stepPlus2)
                val distToCurrentPoly = GeoUtils.distanceToStepPolyline(location, currentStep)
                if (distToStep2Poly < 20.0 && distToStep2Poly < distToCurrentPoly) {
                    _currentStepIndex += 2
                    continue
                }
            }

            break
        }
    }

    fun reset() {
        _currentStepIndex = 0
        _isArrived = false
    }
}
