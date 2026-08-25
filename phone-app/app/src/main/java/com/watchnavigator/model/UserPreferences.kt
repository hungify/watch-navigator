package com.watchnavigator.model

data class UserPreferences(
    val defaultTravelMode: TravelMode = TravelMode.DRIVING,
    val drivingVibrationThresholdMeters: Int = DEFAULT_DRIVING_THRESHOLD_METERS,
    val walkingVibrationThresholdMeters: Int = DEFAULT_WALKING_THRESHOLD_METERS
) {
    fun vibrationThresholdMetersFor(travelMode: TravelMode): Int =
        if (travelMode == TravelMode.WALKING) walkingVibrationThresholdMeters else drivingVibrationThresholdMeters

    companion object {
        const val DEFAULT_DRIVING_THRESHOLD_METERS = 150
        const val DEFAULT_WALKING_THRESHOLD_METERS = 50
        const val MIN_THRESHOLD_METERS = 10
        const val MAX_THRESHOLD_METERS = 1000
    }
}
