package com.watchnavigator.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.watchnavigator.R
import com.watchnavigator.data.SharedPreferencesRepository
import com.watchnavigator.databinding.ActivitySettingsBinding
import com.watchnavigator.model.UserPreferences
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(SharedPreferencesRepository(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        populateFields(viewModel.preferences.value)

        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }

        observeViewModel()
    }

    private fun populateFields(preferences: UserPreferences) {
        binding.etDrivingThreshold.setText(preferences.drivingVibrationThresholdMeters.toString())
        binding.etWalkingThreshold.setText(preferences.walkingVibrationThresholdMeters.toString())
        binding.rgDefaultTravelMode.checkTravelMode(preferences.defaultTravelMode)
    }

    private fun saveSettings() {
        binding.tilDrivingThreshold.error = null
        binding.tilWalkingThreshold.error = null

        val drivingInput =
            binding.etDrivingThreshold.text
                ?.toString()
                ?.trim()
                ?.toIntOrNull()
        if (drivingInput == null) {
            binding.tilDrivingThreshold.error = getString(R.string.error_invalid_number)
            return
        }

        val walkingInput =
            binding.etWalkingThreshold.text
                ?.toString()
                ?.trim()
                ?.toIntOrNull()
        if (walkingInput == null) {
            binding.tilWalkingThreshold.error = getString(R.string.error_invalid_number)
            return
        }

        viewModel.save(binding.rgDefaultTravelMode.selectedTravelMode(), drivingInput, walkingInput)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveResult.collect { result ->
                    when (result) {
                        is SaveSettingsResult.Success -> {
                            Snackbar.make(binding.root, R.string.settings_saved, Snackbar.LENGTH_SHORT).show()
                            viewModel.clearSaveResult()
                        }
                        is SaveSettingsResult.Invalid -> {
                            val message = getString(result.messageResId, result.min, result.max)
                            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                            viewModel.clearSaveResult()
                        }
                        null -> Unit
                    }
                }
            }
        }
    }
}
