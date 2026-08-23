package com.watchnavigator

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.watchnavigator.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isNavigating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState != null) {
            isNavigating = savedInstanceState.getBoolean(KEY_IS_NAVIGATING, false)
        }

        setupUI()
        updateNavigationUI()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_IS_NAVIGATING, isNavigating)
    }

    private fun setupUI() {
        binding.btnNavigate.setOnClickListener {
            toggleNavigation()
        }
    }

    private fun toggleNavigation() {
        isNavigating = !isNavigating
        updateNavigationUI()
    }

    private fun updateNavigationUI() {
        if (isNavigating) {
            binding.tvStatus.text = getString(R.string.status_navigating)
            binding.btnNavigate.text = getString(R.string.btn_stop_navigation)
        } else {
            binding.tvStatus.text = getString(R.string.status_ready)
            binding.btnNavigate.text = getString(R.string.btn_start_navigation)
        }
    }

    companion object {
        private const val KEY_IS_NAVIGATING = "key_is_navigating"
    }
}
