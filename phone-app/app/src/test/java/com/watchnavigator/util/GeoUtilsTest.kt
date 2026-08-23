package com.watchnavigator.util

import com.google.common.truth.Truth.assertThat
import com.watchnavigator.model.LatLng
import com.watchnavigator.model.ManeuverType
import com.watchnavigator.model.NavStep
import org.junit.Test

class GeoUtilsTest {

    @Test
    fun distanceBetweenMeters_identicalPoints_returnsZero() {
        val p = LatLng(21.0285, 105.8542)
        val dist = GeoUtils.distanceBetweenMeters(p, p)
        assertThat(dist).isWithin(0.001).of(0.0)
    }

    @Test
    fun distanceBetweenMeters_knownLocationsInHanoi_calculatesAccurateDistance() {
        // Hanoi Opera House: 21.0243, 105.8576
        // Turtle Tower (Hoan Kiem): 21.0287, 105.8523
        // Distance is ~740 meters
        val opera = LatLng(21.0243, 105.8576)
        val turtleTower = LatLng(21.0287, 105.8523)

        val dist = GeoUtils.distanceBetweenMeters(opera, turtleTower)
        assertThat(dist).isGreaterThan(700.0)
        assertThat(dist).isLessThan(800.0)
    }

    @Test
    fun distanceAlongPolyline_sumsSegmentsCorrectly() {
        val p1 = LatLng(21.0000, 105.0000)
        val p2 = LatLng(21.0010, 105.0000) // ~111 meters north
        val p3 = LatLng(21.0020, 105.0000) // another ~111 meters north

        val polyline = listOf(p1, p2, p3)
        val totalDist = GeoUtils.distanceAlongPolyline(polyline)

        val d1 = GeoUtils.distanceBetweenMeters(p1, p2)
        val d2 = GeoUtils.distanceBetweenMeters(p2, p3)

        assertThat(totalDist).isWithin(0.001).of(d1 + d2)
    }

    @Test
    fun distanceAlongPolyline_emptyOrSinglePoint_returnsZero() {
        assertThat(GeoUtils.distanceAlongPolyline(emptyList())).isEqualTo(0.0)
        assertThat(GeoUtils.distanceAlongPolyline(listOf(LatLng(21.0, 105.0)))).isEqualTo(0.0)
    }

    @Test
    fun projectPointOnSegment_projectsAndClampsCorrectly() {
        val segStart = LatLng(21.0000, 105.0000)
        val segEnd = LatLng(21.0020, 105.0000)

        // Point directly to the right of the midpoint
        val testMid = LatLng(21.0010, 105.0010)
        val projMid = GeoUtils.projectPointOnSegment(testMid, segStart, segEnd)
        assertThat(projMid.latitude).isWithin(0.00001).of(21.0010)
        assertThat(projMid.longitude).isWithin(0.00001).of(105.0000)

        // Point south of segStart -> clamps to segStart
        val testSouth = LatLng(20.9990, 105.0000)
        val projSouth = GeoUtils.projectPointOnSegment(testSouth, segStart, segEnd)
        assertThat(projSouth.latitude).isWithin(0.00001).of(21.0000)
        assertThat(projSouth.longitude).isWithin(0.00001).of(105.0000)

        // Point north of segEnd -> clamps to segEnd
        val testNorth = LatLng(21.0030, 105.0000)
        val projNorth = GeoUtils.projectPointOnSegment(testNorth, segStart, segEnd)
        assertThat(projNorth.latitude).isWithin(0.00001).of(21.0020)
        assertThat(projNorth.longitude).isWithin(0.00001).of(105.0000)
    }

    @Test
    fun findClosestPointOnPolyline_findsNearestSegment() {
        val p1 = LatLng(21.0000, 105.0000)
        val p2 = LatLng(21.0010, 105.0000)
        val p3 = LatLng(21.0010, 105.0020) // 90 degree turn east

        val polyline = listOf(p1, p2, p3)

        // Point near first segment
        val testP1 = LatLng(21.0005, 105.0001)
        val proj1 = GeoUtils.findClosestPointOnPolyline(testP1, polyline)
        assertThat(proj1.segmentIndex).isEqualTo(0)
        assertThat(proj1.projectedPoint.latitude).isWithin(0.0001).of(21.0005)

        // Point near second segment
        val testP2 = LatLng(21.0011, 105.0015)
        val proj2 = GeoUtils.findClosestPointOnPolyline(testP2, polyline)
        assertThat(proj2.segmentIndex).isEqualTo(1)
        assertThat(proj2.projectedPoint.longitude).isWithin(0.0001).of(105.0015)
    }

    @Test
    fun remainingDistanceAlongPolyline_calculatesRemainingLength() {
        val p1 = LatLng(21.0000, 105.0000)
        val p2 = LatLng(21.0010, 105.0000)
        val p3 = LatLng(21.0020, 105.0000)
        val polyline = listOf(p1, p2, p3)

        // If user is at p1, remaining distance should equal total polyline distance
        val atStart = GeoUtils.remainingDistanceAlongPolyline(p1, polyline)
        val total = GeoUtils.distanceAlongPolyline(polyline)
        assertThat(atStart).isWithin(0.5).of(total)

        // If user is at p2, remaining distance should be distance p2->p3
        val atP2 = GeoUtils.remainingDistanceAlongPolyline(p2, polyline)
        val distP2P3 = GeoUtils.distanceBetweenMeters(p2, p3)
        assertThat(atP2).isWithin(0.5).of(distP2P3)

        // If user is at p3 (end), remaining distance is near 0
        val atEnd = GeoUtils.remainingDistanceAlongPolyline(p3, polyline)
        assertThat(atEnd).isWithin(0.5).of(0.0)
    }

    @Test
    fun remainingDistanceAlongStep_withPolylineAndWithoutPolyline() {
        val start = LatLng(21.0000, 105.0000)
        val mid = LatLng(21.0010, 105.0000)
        val end = LatLng(21.0020, 105.0000)

        val stepWithPoly = NavStep(
            instruction = "Go straight",
            streetName = "Main St",
            maneuver = ManeuverType.STRAIGHT,
            distanceMeters = 222,
            durationSeconds = 60,
            startLocation = start,
            endLocation = end,
            polylinePoints = listOf(start, mid, end)
        )

        val distWithPoly = GeoUtils.remainingDistanceAlongStep(mid, stepWithPoly)
        assertThat(distWithPoly).isGreaterThan(100)
        assertThat(distWithPoly).isLessThan(125)

        val stepWithoutPoly = NavStep(
            instruction = "Go straight",
            streetName = "Main St",
            maneuver = ManeuverType.STRAIGHT,
            distanceMeters = 222,
            durationSeconds = 60,
            startLocation = start,
            endLocation = end,
            polylinePoints = emptyList()
        )

        val distWithoutPoly = GeoUtils.remainingDistanceAlongStep(mid, stepWithoutPoly)
        assertThat(distWithoutPoly).isGreaterThan(100)
        assertThat(distWithoutPoly).isLessThan(125)
    }

    @Test
    fun distanceToStepPolyline_calculatesCrossTrackDistance() {
        val start = LatLng(21.0000, 105.0000)
        val end = LatLng(21.0020, 105.0000)

        val step = NavStep(
            instruction = "Go straight",
            streetName = "Main St",
            maneuver = ManeuverType.STRAIGHT,
            distanceMeters = 222,
            durationSeconds = 60,
            startLocation = start,
            endLocation = end,
            polylinePoints = listOf(start, end)
        )

        // Point is on the line -> distance is 0
        val onLine = LatLng(21.0010, 105.0000)
        val distOnLine = GeoUtils.distanceToStepPolyline(onLine, step)
        assertThat(distOnLine).isWithin(0.1).of(0.0)

        // Point is ~100m to the east
        val offLine = LatLng(21.0010, 105.0010)
        val distOffLine = GeoUtils.distanceToStepPolyline(offLine, step)
        assertThat(distOffLine).isGreaterThan(90.0)
        assertThat(distOffLine).isLessThan(120.0)
    }
}
