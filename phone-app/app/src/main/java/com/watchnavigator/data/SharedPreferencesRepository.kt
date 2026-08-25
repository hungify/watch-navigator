package com.watchnavigator.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.watchnavigator.model.TravelMode
import com.watchnavigator.model.UserPreferences

class SharedPreferencesRepository(
    context: Context,
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
) : PreferencesRepository {

    override fun getUserPreferences(): UserPreferences {
        val travelModeValue = prefs.getString(KEY_DEFAULT_TRAVEL_MODE, TravelMode.DRIVING.apiValue)
            ?: TravelMode.DRIVING.apiValue
        return UserPreferences(
            defaultTravelMode = TravelMode.fromApiValue(travelModeValue),
            drivingVibrationThresholdMeters = prefs.getInt(
                KEY_DRIVING_THRESHOLD,
                UserPreferences.DEFAULT_DRIVING_THRESHOLD_METERS
            ),
            walkingVibrationThresholdMeters = prefs.getInt(
                KEY_WALKING_THRESHOLD,
                UserPreferences.DEFAULT_WALKING_THRESHOLD_METERS
            )
        )
    }

    override fun savePreferences(preferences: UserPreferences) {
        prefs.edit {
            putString(KEY_DEFAULT_TRAVEL_MODE, preferences.defaultTravelMode.apiValue)
            putInt(KEY_DRIVING_THRESHOLD, preferences.drivingVibrationThresholdMeters)
            putInt(KEY_WALKING_THRESHOLD, preferences.walkingVibrationThresholdMeters)
        }
    }

    companion object {
        private const val PREFS_NAME = "watch_navigator_prefs"
        private const val KEY_DEFAULT_TRAVEL_MODE = "default_travel_mode"
        private const val KEY_DRIVING_THRESHOLD = "driving_vibration_threshold_m"
        private const val KEY_WALKING_THRESHOLD = "walking_vibration_threshold_m"
    }
}
