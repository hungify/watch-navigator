package com.watchnavigator.data

import com.watchnavigator.model.UserPreferences

interface PreferencesRepository {
    fun getUserPreferences(): UserPreferences
    fun savePreferences(preferences: UserPreferences)
}
