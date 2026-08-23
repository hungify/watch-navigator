package com.watchnavigator.engine

import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import com.watchnavigator.model.TravelMode

object LocationRequestConfig {

    const val DRIVING_INTERVAL_MS = 1000L
    const val DRIVING_MIN_UPDATE_INTERVAL_MS = 500L
    const val DRIVING_SMALLEST_DISPLACEMENT_METERS = 0f

    const val WALKING_INTERVAL_MS = 4000L
    const val WALKING_MIN_UPDATE_INTERVAL_MS = 2000L
    const val WALKING_SMALLEST_DISPLACEMENT_METERS = 3f

    fun getIntervalMs(mode: TravelMode): Long = when (mode) {
        TravelMode.DRIVING -> DRIVING_INTERVAL_MS
        TravelMode.WALKING -> WALKING_INTERVAL_MS
    }

    fun getMinUpdateIntervalMs(mode: TravelMode): Long = when (mode) {
        TravelMode.DRIVING -> DRIVING_MIN_UPDATE_INTERVAL_MS
        TravelMode.WALKING -> WALKING_MIN_UPDATE_INTERVAL_MS
    }

    fun getSmallestDisplacementMeters(mode: TravelMode): Float = when (mode) {
        TravelMode.DRIVING -> DRIVING_SMALLEST_DISPLACEMENT_METERS
        TravelMode.WALKING -> WALKING_SMALLEST_DISPLACEMENT_METERS
    }

    /**
     * Builds a FusedLocationProviderClient LocationRequest optimized for the given travel mode.
     */
    fun createLocationRequest(mode: TravelMode): LocationRequest {
        val interval = getIntervalMs(mode)
        val minInterval = getMinUpdateIntervalMs(mode)
        val displacement = getSmallestDisplacementMeters(mode)

        return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(minInterval)
            .setMinUpdateDistanceMeters(displacement)
            .build()
    }
}
