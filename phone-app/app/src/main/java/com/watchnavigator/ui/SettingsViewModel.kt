package com.watchnavigator.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.watchnavigator.R
import com.watchnavigator.data.PreferencesRepository
import com.watchnavigator.model.TravelMode
import com.watchnavigator.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class SaveSettingsResult {
    object Success : SaveSettingsResult()
    data class Invalid(@StringRes val messageResId: Int, val min: Int, val max: Int) : SaveSettingsResult()
}
class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _preferences = MutableStateFlow(preferencesRepository.getUserPreferences())
    val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    private val _saveResult = MutableStateFlow<SaveSettingsResult?>(null)
    val saveResult: StateFlow<SaveSettingsResult?> = _saveResult.asStateFlow()

    fun save(defaultTravelMode: TravelMode, drivingThresholdMeters: Int, walkingThresholdMeters: Int) {
        val range = UserPreferences.MIN_THRESHOLD_METERS..UserPreferences.MAX_THRESHOLD_METERS

        if (drivingThresholdMeters !in range) {
            _saveResult.value = SaveSettingsResult.Invalid(
                R.string.error_driving_threshold_range,
                range.first,
                range.last
            )
            return
        }

        if (walkingThresholdMeters !in range) {
            _saveResult.value = SaveSettingsResult.Invalid(
                R.string.error_walking_threshold_range,
                range.first,
                range.last
            )
            return
        }

        val updated = UserPreferences(
            defaultTravelMode = defaultTravelMode,
            drivingVibrationThresholdMeters = drivingThresholdMeters,
            walkingVibrationThresholdMeters = walkingThresholdMeters
        )
        preferencesRepository.savePreferences(updated)
        _preferences.value = updated
        _saveResult.value = SaveSettingsResult.Success
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }

    class Factory(private val preferencesRepository: PreferencesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(preferencesRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
