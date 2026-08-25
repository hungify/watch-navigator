package com.watchnavigator.data

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import com.watchnavigator.model.TravelMode
import com.watchnavigator.model.UserPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class SharedPreferencesRepositoryTest {
    private val context: Context = mockk(relaxed = true)
    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)
    private lateinit var repository: SharedPreferencesRepository

    @Before
    fun setUp() {
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        repository = SharedPreferencesRepository(context, prefs)
    }

    @Test
    fun getUserPreferences_whenNoStoredValues_returnsDefaults() {
        every { prefs.getString(any(), any()) } answers { secondArg() }
        every { prefs.getInt(any(), any()) } answers { secondArg() }

        val prefsResult = repository.getUserPreferences()

        assertThat(prefsResult.defaultTravelMode).isEqualTo(TravelMode.DRIVING)
        assertThat(prefsResult.drivingVibrationThresholdMeters).isEqualTo(UserPreferences.DEFAULT_DRIVING_THRESHOLD_METERS)
        assertThat(prefsResult.walkingVibrationThresholdMeters).isEqualTo(UserPreferences.DEFAULT_WALKING_THRESHOLD_METERS)
    }

    @Test
    fun getUserPreferences_whenValuesStored_returnsStoredValues() {
        every { prefs.getString(any(), any()) } returns TravelMode.WALKING.apiValue
        every { prefs.getInt("driving_vibration_threshold_m", any()) } returns 300
        every { prefs.getInt("walking_vibration_threshold_m", any()) } returns 25

        val prefsResult = repository.getUserPreferences()

        assertThat(prefsResult.defaultTravelMode).isEqualTo(TravelMode.WALKING)
        assertThat(prefsResult.drivingVibrationThresholdMeters).isEqualTo(300)
        assertThat(prefsResult.walkingVibrationThresholdMeters).isEqualTo(25)
    }

    @Test
    fun savePreferences_writesAllFieldsAndCommits() {
        val toSave =
            UserPreferences(
                defaultTravelMode = TravelMode.WALKING,
                drivingVibrationThresholdMeters = 175,
                walkingVibrationThresholdMeters = 60
            )

        val travelModeSlot = slot<String>()
        val drivingSlot = slot<Int>()
        val walkingSlot = slot<Int>()
        every { editor.putString("default_travel_mode", capture(travelModeSlot)) } returns editor
        every { editor.putInt("driving_vibration_threshold_m", capture(drivingSlot)) } returns editor
        every { editor.putInt("walking_vibration_threshold_m", capture(walkingSlot)) } returns editor

        repository.savePreferences(toSave)

        assertThat(travelModeSlot.captured).isEqualTo("walking")
        assertThat(drivingSlot.captured).isEqualTo(175)
        assertThat(walkingSlot.captured).isEqualTo(60)
        verify { editor.apply() }
    }
}
