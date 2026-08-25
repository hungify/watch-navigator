package com.watchnavigator.engine

import com.google.android.gms.location.Priority
import com.google.common.truth.Truth.assertThat
import com.watchnavigator.model.TravelMode
import org.junit.Test

class LocationRequestConfigTest {
    @Test
    fun drivingMode_hasOneSecondIntervalAndZeroDisplacement() {
        val interval = LocationRequestConfig.getIntervalMs(TravelMode.DRIVING)
        val minInterval = LocationRequestConfig.getMinUpdateIntervalMs(TravelMode.DRIVING)
        val displacement = LocationRequestConfig.getSmallestDisplacementMeters(TravelMode.DRIVING)

        assertThat(interval).isEqualTo(1000L)
        assertThat(minInterval).isEqualTo(500L)
        assertThat(displacement).isEqualTo(0f)
    }

    @Test
    fun walkingMode_hasFourSecondIntervalAndThreeMetersDisplacement() {
        val interval = LocationRequestConfig.getIntervalMs(TravelMode.WALKING)
        val minInterval = LocationRequestConfig.getMinUpdateIntervalMs(TravelMode.WALKING)
        val displacement = LocationRequestConfig.getSmallestDisplacementMeters(TravelMode.WALKING)

        assertThat(interval).isEqualTo(4000L)
        assertThat(minInterval).isEqualTo(2000L)
        assertThat(displacement).isEqualTo(3f)
    }

    @Test
    fun createLocationRequest_createsProperRequestForDriving() {
        val request = LocationRequestConfig.createLocationRequest(TravelMode.DRIVING)

        assertThat(request.priority).isEqualTo(Priority.PRIORITY_HIGH_ACCURACY)
        assertThat(request.intervalMillis).isEqualTo(1000L)
        assertThat(request.minUpdateIntervalMillis).isEqualTo(500L)
        assertThat(request.minUpdateDistanceMeters).isEqualTo(0f)
    }

    @Test
    fun createLocationRequest_createsProperRequestForWalking() {
        val request = LocationRequestConfig.createLocationRequest(TravelMode.WALKING)

        assertThat(request.priority).isEqualTo(Priority.PRIORITY_HIGH_ACCURACY)
        assertThat(request.intervalMillis).isEqualTo(4000L)
        assertThat(request.minUpdateIntervalMillis).isEqualTo(2000L)
        assertThat(request.minUpdateDistanceMeters).isEqualTo(3f)
    }
}
