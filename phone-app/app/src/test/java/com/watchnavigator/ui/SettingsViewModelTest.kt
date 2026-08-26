package com.watchnavigator.ui

import com.google.common.truth.Truth.assertThat
import com.watchnavigator.R
import com.watchnavigator.data.PreferencesRepository
import com.watchnavigator.model.TravelMode
import com.watchnavigator.model.UserPreferences
import org.junit.Before
import org.junit.Test

class SettingsViewModelTest {
    private lateinit var fakeRepository: FakePreferencesRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        fakeRepository = FakePreferencesRepository()
        viewModel = SettingsViewModel(fakeRepository)
    }

    @Test
    fun preferences_initializesFromRepository() {
        fakeRepository.stored =
            UserPreferences(
                defaultTravelMode = TravelMode.WALKING,
                drivingVibrationThresholdMeters = 200,
                walkingVibrationThresholdMeters = 40
            )
        val vm = SettingsViewModel(fakeRepository)

        assertThat(vm.preferences.value.defaultTravelMode).isEqualTo(TravelMode.WALKING)
        assertThat(vm.preferences.value.drivingVibrationThresholdMeters).isEqualTo(200)
        assertThat(vm.preferences.value.walkingVibrationThresholdMeters).isEqualTo(40)
    }

    @Test
    fun save_withValidValues_persistsAndUpdatesState() {
        viewModel.save(TravelMode.WALKING, 175, 60)

        assertThat(viewModel.saveResult.value).isEqualTo(SaveSettingsResult.Success)
        assertThat(viewModel.preferences.value.defaultTravelMode).isEqualTo(TravelMode.WALKING)
        assertThat(viewModel.preferences.value.drivingVibrationThresholdMeters).isEqualTo(175)
        assertThat(viewModel.preferences.value.walkingVibrationThresholdMeters).isEqualTo(60)
        assertThat(fakeRepository.stored.drivingVibrationThresholdMeters).isEqualTo(175)
    }

    @Test
    fun save_withDrivingThresholdBelowMinimum_rejectsAndDoesNotPersist() {
        viewModel.save(TravelMode.DRIVING, 1, 60)

        val result = viewModel.saveResult.value
        assertThat(result).isInstanceOf(SaveSettingsResult.Invalid::class.java)
        val invalid = result as SaveSettingsResult.Invalid
        assertThat(invalid.messageResId).isEqualTo(R.string.error_driving_threshold_range)
        assertThat(invalid.min).isEqualTo(UserPreferences.MIN_THRESHOLD_METERS)
        assertThat(invalid.max).isEqualTo(UserPreferences.MAX_THRESHOLD_METERS)
        assertThat(fakeRepository.savedCount).isEqualTo(0)
    }

    @Test
    fun save_withWalkingThresholdAboveMaximum_rejectsAndDoesNotPersist() {
        viewModel.save(TravelMode.DRIVING, 150, 5000)

        val result = viewModel.saveResult.value
        assertThat(result).isInstanceOf(SaveSettingsResult.Invalid::class.java)
        val invalid = result as SaveSettingsResult.Invalid
        assertThat(invalid.messageResId).isEqualTo(R.string.error_walking_threshold_range)
        assertThat(invalid.min).isEqualTo(UserPreferences.MIN_THRESHOLD_METERS)
        assertThat(invalid.max).isEqualTo(UserPreferences.MAX_THRESHOLD_METERS)
        assertThat(fakeRepository.savedCount).isEqualTo(0)
    }

    @Test
    fun save_withBoundaryValues_minAndMax_succeeds() {
        viewModel.save(
            TravelMode.DRIVING,
            UserPreferences.MIN_THRESHOLD_METERS,
            UserPreferences.MAX_THRESHOLD_METERS
        )

        assertThat(viewModel.saveResult.value).isEqualTo(SaveSettingsResult.Success)
        assertThat(viewModel.preferences.value.drivingVibrationThresholdMeters)
            .isEqualTo(UserPreferences.MIN_THRESHOLD_METERS)
        assertThat(viewModel.preferences.value.walkingVibrationThresholdMeters)
            .isEqualTo(UserPreferences.MAX_THRESHOLD_METERS)
    }

    @Test
    fun save_withWalkingThresholdBelowMinimum_rejectsAndDoesNotPersist() {
        viewModel.save(TravelMode.WALKING, 100, UserPreferences.MIN_THRESHOLD_METERS - 1)

        val result = viewModel.saveResult.value
        assertThat(result).isInstanceOf(SaveSettingsResult.Invalid::class.java)
        val invalid = result as SaveSettingsResult.Invalid
        assertThat(invalid.messageResId).isEqualTo(R.string.error_walking_threshold_range)
        assertThat(fakeRepository.savedCount).isEqualTo(0)
    }

    @Test
    fun save_withDrivingThresholdAboveMaximum_rejectsAndDoesNotPersist() {
        viewModel.save(TravelMode.DRIVING, UserPreferences.MAX_THRESHOLD_METERS + 1, 50)

        val result = viewModel.saveResult.value
        assertThat(result).isInstanceOf(SaveSettingsResult.Invalid::class.java)
        val invalid = result as SaveSettingsResult.Invalid
        assertThat(invalid.messageResId).isEqualTo(R.string.error_driving_threshold_range)
        assertThat(fakeRepository.savedCount).isEqualTo(0)
    }

    @Test
    fun factory_createsSettingsViewModelSuccessfully() {
        val factory = SettingsViewModel.Factory(fakeRepository)
        val createdVm = factory.create(SettingsViewModel::class.java)

        assertThat(createdVm).isNotNull()
        assertThat(createdVm.preferences.value).isEqualTo(fakeRepository.stored)
    }

    @Test
    fun clearSaveResult_resetsToNull() {
        viewModel.save(TravelMode.DRIVING, 150, 50)
        assertThat(viewModel.saveResult.value).isNotNull()

        viewModel.clearSaveResult()

        assertThat(viewModel.saveResult.value).isNull()
    }

    class FakePreferencesRepository(
        var stored: UserPreferences = UserPreferences()
    ) : PreferencesRepository {
        var savedCount = 0

        override fun getUserPreferences(): UserPreferences = stored

        override fun savePreferences(preferences: UserPreferences) {
            stored = preferences
            savedCount++
        }
    }
}
