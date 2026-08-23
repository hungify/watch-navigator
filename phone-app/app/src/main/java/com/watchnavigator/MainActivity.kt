package com.watchnavigator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.watchnavigator.data.GoogleDirectionsService
import com.watchnavigator.data.GooglePlacesSearchService
import com.watchnavigator.databinding.ActivityMainBinding
import com.watchnavigator.model.LatLng
import com.watchnavigator.model.TravelMode
import com.watchnavigator.ui.MainViewModel
import com.watchnavigator.ui.PlaceSuggestionsAdapter
import com.watchnavigator.ui.RouteUiState
import com.watchnavigator.ui.SuggestionsUiState
import com.watchnavigator.ui.TurnStepsAdapter
import com.watchnavigator.util.DistanceFormatter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isNavigating = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var placesClient: PlacesClient? = null
    private lateinit var suggestionsAdapter: PlaceSuggestionsAdapter
    private lateinit var stepsAdapter: TurnStepsAdapter

    private val viewModel: MainViewModel by viewModels {
        val placesSearchService = GooglePlacesSearchService(placesClient)
        val directionsService = GoogleDirectionsService(BuildConfig.MAPS_API_KEY)
        MainViewModel.Factory(placesSearchService, directionsService)
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineLocationGranted || coarseLocationGranted) {
            fetchDeviceLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initPlacesSdk()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (savedInstanceState != null) {
            isNavigating = savedInstanceState.getBoolean(KEY_IS_NAVIGATING, false)
        }

        setupAdapters()
        setupUI()
        observeViewModel()
        checkLocationPermissionAndFetch()
    }

    private fun initPlacesSdk() {
        if (!Places.isInitialized()) {
            val apiKey = BuildConfig.MAPS_API_KEY
            if (apiKey.isNotBlank()) {
                Places.initialize(applicationContext, apiKey)
            }
        }
        if (Places.isInitialized()) {
            placesClient = Places.createClient(this)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_IS_NAVIGATING, isNavigating)
    }

    private fun setupAdapters() {
        suggestionsAdapter = PlaceSuggestionsAdapter { suggestion ->
            viewModel.selectSuggestion(suggestion)
            binding.etDestination.setText(suggestion.primaryText)
            binding.etDestination.setSelection(suggestion.primaryText.length)
            binding.cardSuggestions.visibility = View.GONE
        }
        binding.rvSuggestions.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = suggestionsAdapter
        }

        stepsAdapter = TurnStepsAdapter()
        binding.rvSteps.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = stepsAdapter
        }
    }

    private fun setupUI() {
        binding.etDestination.doAfterTextChanged { text ->
            suggestionsAdapter.submitList(emptyList())
            binding.cardSuggestions.visibility = View.GONE
            viewModel.onQueryChanged(text?.toString() ?: "")
        }

        binding.rgTravelMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.rbWalking) TravelMode.WALKING else TravelMode.DRIVING
            viewModel.setTravelMode(mode)
        }

        binding.btnRetry.setOnClickListener {
            viewModel.retryRouteCalculation()
        }

        binding.btnNavigate.setOnClickListener {
            toggleNavigation()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.suggestionsState.collect { state ->
                        when (state) {
                            is SuggestionsUiState.Idle -> {
                                binding.tilDestination.error = null
                                binding.cardSuggestions.visibility = View.GONE
                                binding.pbSuggestionsLoading.visibility = View.GONE
                            }
                            is SuggestionsUiState.Loading -> {
                                binding.tilDestination.error = null
                                binding.cardSuggestions.visibility = View.VISIBLE
                                binding.pbSuggestionsLoading.visibility = View.VISIBLE
                            }
                            is SuggestionsUiState.Success -> {
                                binding.tilDestination.error = null
                                binding.pbSuggestionsLoading.visibility = View.GONE
                                if (state.suggestions.isEmpty()) {
                                    binding.cardSuggestions.visibility = View.GONE
                                } else {
                                    binding.cardSuggestions.visibility = View.VISIBLE
                                    suggestionsAdapter.submitList(state.suggestions)
                                }
                            }
                            is SuggestionsUiState.Error -> {
                                binding.cardSuggestions.visibility = View.GONE
                                binding.pbSuggestionsLoading.visibility = View.GONE
                                binding.tilDestination.error = state.message
                            }
                        }
                    }
                }

                launch {
                    viewModel.routeState.collect { state ->
                        when (state) {
                            is RouteUiState.Idle -> {
                                binding.layoutRouteLoading.visibility = View.GONE
                                binding.cardRouteOverview.visibility = View.GONE
                                binding.cardSteps.visibility = View.GONE
                                binding.cardError.visibility = View.GONE
                                updateNavigationUI()
                            }
                            is RouteUiState.Loading -> {
                                binding.layoutRouteLoading.visibility = View.VISIBLE
                                binding.cardRouteOverview.visibility = View.GONE
                                binding.cardSteps.visibility = View.GONE
                                binding.cardError.visibility = View.GONE
                                binding.tvStatus.text = getString(R.string.calculating_route)
                            }
                            is RouteUiState.Success -> {
                                binding.layoutRouteLoading.visibility = View.GONE
                                binding.cardError.visibility = View.GONE

                                val route = state.route
                                binding.tvRouteDestination.text = route.destinationAddress.ifBlank {
                                    viewModel.selectedDestination.value?.primaryText ?: getString(R.string.title_destination)
                                }
                                binding.tvRouteDuration.text = DistanceFormatter.formatDuration(route.totalDurationSeconds)
                                binding.tvRouteDistance.text = DistanceFormatter.formatDistance(route.totalDistanceMeters)
                                binding.cardRouteOverview.visibility = View.VISIBLE

                                if (route.steps.isNotEmpty()) {
                                    binding.tvStepsHeader.text = getString(R.string.title_turn_steps) + " (${route.steps.size})"
                                    stepsAdapter.submitList(route.steps)
                                    binding.cardSteps.visibility = View.VISIBLE
                                } else {
                                    binding.cardSteps.visibility = View.GONE
                                }

                                binding.tvStatus.text = getString(R.string.status_route_ready)
                            }
                            is RouteUiState.Error -> {
                                binding.layoutRouteLoading.visibility = View.GONE
                                binding.cardRouteOverview.visibility = View.GONE
                                binding.cardSteps.visibility = View.GONE
                                binding.cardError.visibility = View.VISIBLE
                                binding.tvErrorMessage.text = state.message
                                binding.tvStatus.text = getString(R.string.status_ready)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkLocationPermissionAndFetch() {
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fineLocation == PackageManager.PERMISSION_GRANTED || coarseLocation == PackageManager.PERMISSION_GRANTED) {
            fetchDeviceLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun fetchDeviceLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        viewModel.setCurrentLocation(LatLng(location.latitude, location.longitude))
                    }
                }
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
            val isRouteLoaded = viewModel.routeState.value is RouteUiState.Success
            binding.tvStatus.text = if (isRouteLoaded) getString(R.string.status_route_ready) else getString(R.string.status_ready)
            binding.btnNavigate.text = getString(R.string.btn_start_navigation)
        }
    }

    companion object {
        private const val KEY_IS_NAVIGATING = "key_is_navigating"
        val DEFAULT_LOCATION = LatLng(21.028511, 105.854167)
    }
}
