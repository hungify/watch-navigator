package com.watchnavigator

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertThat(2 + 2).isEqualTo(4)
    }

    @Test
    fun buildConfig_applicationId_isExpected() {
        assertThat(BuildConfig.APPLICATION_ID).isEqualTo("com.watchnavigator")
    }

    @Test
    fun buildConfig_mapsApiKey_isNotNull() {
        assertThat(BuildConfig.MAPS_API_KEY).isNotNull()
    }
}
