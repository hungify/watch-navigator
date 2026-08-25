package com.watchnavigator.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UserPreferencesTest {
    @Test
    fun defaultConstructor_usesDrivingModeAndDefaultThresholds() {
        val prefs = UserPreferences()

        assertThat(prefs.defaultTravelMode).isEqualTo(TravelMode.DRIVING)
        assertThat(prefs.drivingVibrationThresholdMeters).isEqualTo(150)
        assertThat(prefs.walkingVibrationThresholdMeters).isEqualTo(50)
    }

    @Test
    fun vibrationThresholdMetersFor_driving_returnsDrivingThreshold() {
        val prefs = UserPreferences(drivingVibrationThresholdMeters = 200, walkingVibrationThresholdMeters = 40)

        assertThat(prefs.vibrationThresholdMetersFor(TravelMode.DRIVING)).isEqualTo(200)
    }

    @Test
    fun vibrationThresholdMetersFor_walking_returnsWalkingThreshold() {
        val prefs = UserPreferences(drivingVibrationThresholdMeters = 200, walkingVibrationThresholdMeters = 40)

        assertThat(prefs.vibrationThresholdMetersFor(TravelMode.WALKING)).isEqualTo(40)
    }
}
