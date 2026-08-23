package com.watchnavigator.util

import com.watchnavigator.model.LatLng
import com.watchnavigator.model.NavStep
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {

    const val EARTH_RADIUS_METERS = 6371000.0

    data class PolylineProjection(
        val projectedPoint: LatLng,
        val segmentIndex: Int,
        val distanceToPolylineMeters: Double
    )

    /**
     * Calculates the great-circle distance between two coordinates in meters using the Haversine formula.
     */
    fun distanceBetweenMeters(p1: LatLng, p2: LatLng): Double {
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLng = Math.toRadians(p2.longitude - p1.longitude)
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)

        val a = sin(dLat / 2).let { it * it } +
                sin(dLng / 2).let { it * it } * cos(lat1) * cos(lat2)
        val c = 2 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Calculates the total length in meters along a polyline.
     */
    fun distanceAlongPolyline(points: List<LatLng>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += distanceBetweenMeters(points[i], points[i + 1])
        }
        return total
    }

    /**
     * Projects a point onto a line segment and clamps the result to the segment boundaries.
     */
    fun projectPointOnSegment(point: LatLng, segStart: LatLng, segEnd: LatLng): LatLng {
        val meanLat = (segStart.latitude + segEnd.latitude) / 2.0
        val cosMeanLat = cos(Math.toRadians(meanLat))

        val dx = (segEnd.longitude - segStart.longitude) * cosMeanLat
        val dy = segEnd.latitude - segStart.latitude

        val lenSq = dx * dx + dy * dy
        if (lenSq == 0.0) return segStart

        val px = (point.longitude - segStart.longitude) * cosMeanLat
        val py = point.latitude - segStart.latitude

        val t = (px * dx + py * dy) / lenSq
        val tClamped = t.coerceIn(0.0, 1.0)

        val projLat = segStart.latitude + tClamped * (segEnd.latitude - segStart.latitude)
        val projLng = segStart.longitude + tClamped * (segEnd.longitude - segStart.longitude)

        return LatLng(projLat, projLng)
    }

    /**
     * Finds the closest point on a polyline to the given coordinate.
     */
    fun findClosestPointOnPolyline(point: LatLng, polyline: List<LatLng>): PolylineProjection {
        if (polyline.isEmpty()) {
            return PolylineProjection(point, 0, 0.0)
        }
        if (polyline.size == 1) {
            val dist = distanceBetweenMeters(point, polyline[0])
            return PolylineProjection(polyline[0], 0, dist)
        }

        var closestProj = polyline[0]
        var closestSegmentIndex = 0
        var minDistance = Double.MAX_VALUE

        for (i in 0 until polyline.size - 1) {
            val proj = projectPointOnSegment(point, polyline[i], polyline[i + 1])
            val dist = distanceBetweenMeters(point, proj)
            if (dist < minDistance) {
                minDistance = dist
                closestProj = proj
                closestSegmentIndex = i
            }
        }

        return PolylineProjection(closestProj, closestSegmentIndex, minDistance)
    }

    /**
     * Calculates the remaining distance from the user's projected location along the polyline to its end.
     */
    fun remainingDistanceAlongPolyline(point: LatLng, polyline: List<LatLng>): Double {
        if (polyline.isEmpty()) return 0.0
        if (polyline.size == 1) return distanceBetweenMeters(point, polyline[0])

        val projection = findClosestPointOnPolyline(point, polyline)
        var remainingDist = distanceBetweenMeters(projection.projectedPoint, polyline[projection.segmentIndex + 1])

        for (i in (projection.segmentIndex + 1) until (polyline.size - 1)) {
            remainingDist += distanceBetweenMeters(polyline[i], polyline[i + 1])
        }

        return remainingDist
    }

    /**
     * Calculates remaining distance in meters along a NavStep to its end point.
     */
    fun remainingDistanceAlongStep(point: LatLng, step: NavStep): Int {
        return if (step.polylinePoints.size >= 2) {
            remainingDistanceAlongPolyline(point, step.polylinePoints).roundToInt()
        } else {
            distanceBetweenMeters(point, step.endLocation).roundToInt()
        }
    }

    /**
     * Calculates the shortest perpendicular / cross-track distance in meters from a point to a step's polyline.
     */
    fun distanceToStepPolyline(point: LatLng, step: NavStep): Double {
        return when {
            step.polylinePoints.size >= 2 -> {
                findClosestPointOnPolyline(point, step.polylinePoints).distanceToPolylineMeters
            }
            step.polylinePoints.size == 1 -> {
                distanceBetweenMeters(point, step.polylinePoints[0])
            }
            else -> {
                val proj = projectPointOnSegment(point, step.startLocation, step.endLocation)
                distanceBetweenMeters(point, proj)
            }
        }
    }
}
