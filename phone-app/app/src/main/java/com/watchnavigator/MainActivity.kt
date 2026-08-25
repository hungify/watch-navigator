package com.watchnavigator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
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
import com.watchnavigator.data.HuaweiWearEngineService
import com.watchnavigator.data.SharedPreferencesRepository
import com.watchnavigator.databinding.ActivityMainBinding
import com.watchnavigator.engine.NavigationProgress
import com.watchnavigator.model.LatLng
import com.watchnavigator.model.WatchConnectionState
import com.watchnavigator.service.NavigationForegroundService
import com.watchnavigator.ui.MainViewModel
import com.watchnavigator.ui.PlaceSuggestionsAdapter
import com.watchnavigator.ui.RouteUiState
import com.watchnavigator.ui.SettingsActivity
import com.watchnavigator.ui.SuggestionsUiState
import com.watchnavigator.ui.TurnStepsAdapter
import com.watchnavigator.ui.checkTravelMode
import com.watchnavigator.ui.selectedTravelMode
import com.watchnavigator.util.DistanceFormatter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var placesClient: PlacesClient? = null
    private lateinit var suggestionsAdapter: PlaceSuggestionsAdapter
    private lateinit var stepsAdapter: TurnStepsAdapter

    private val viewModel: MainViewModel by viewModels {
        val placesSearchService = GooglePlacesSearchService(placesClient)
        val directionsService = GoogleDirectionsService(
            apiKey = BuildConfig.MAPS_API_KEY,
            serverUrl = BuildConfig.NAV_SERVER_URL,
            serverToken = BuildConfig.NAV_SERVER_TOKEN
        )
        val wearEngineService = HuaweiWearEngineService(applicationContext)
        val preferencesRepository = SharedPreferencesRepository(applicationContext)
        MainViewModel.Factory(placesSearchService, directionsService, wearEngineService, preferencesRepository)
    }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineLocationGranted || coarseLocationGranted) {
            fetchDeviceLocation()
        }
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshTravelModeFromPreferences()
        binding.rgTravelMode.checkTravelMode(viewModel.travelMode.value)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initPlacesSdk()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupAdapters()
        setupUI()
        observeViewModel()
        checkPermissionsAndFetchLocation()
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
        binding.toolbar.inflateMenu(R.menu.menu_main)
        binding.toolbar.setOnMenuItemClickListener { menuItem -> onMenuItemSelected(menuItem) }

        binding.rgTravelMode.checkTravelMode(viewModel.travelMode.value)

        binding.etDestination.doAfterTextChanged { text ->
            suggestionsAdapter.submitList(emptyList())
            binding.cardSuggestions.visibility = View.GONE
            viewModel.onQueryChanged(text?.toString() ?: "")
        }

        binding.rgTravelMode.setOnCheckedChangeListener { _, _ ->
            viewModel.setTravelMode(binding.rgTravelMode.selectedTravelMode())
        }

        binding.btnRetry.setOnClickListener {
            viewModel.retryRouteCalculation()
        }

        binding.btnConnectWatch.setOnClickListener {
            viewModel.checkWatchConnection()
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
                                updateNavigationUI(viewModel.isNavigating.value)
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

                                if (!viewModel.isNavigating.value) {
                                    binding.tvStatus.text = getString(R.string.status_route_ready)
                                }
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

                launch {
                    combine(
                        viewModel.watchConnectionState,
                        viewModel.watchSendError
                    ) { connectionState, sendError ->
                        connectionState to sendError
                    }.collect { (state, sendError) ->
                        updateWatchStatusUI(state, sendError)
                    }
                }

                launch {
                    viewModel.isNavigating.collect { isNavigating ->
                        updateNavigationUI(isNavigating)
                        if (!isNavigating) {
                            stepsAdapter.clearActiveStep()
                        }
                    }
                }
                launch {
                    viewModel.isRecalculating.collect { isRecalculating ->
                        if (viewModel.isNavigating.value) {
                            if (isRecalculating) {
                                binding.tvStatus.text = getString(R.string.status_recalculating)
                            } else {
                                renderNavigationStatus(viewModel.navigationProgress.value)
                            }
                        }
                    }
                }

                launch {
                    var lastScrolledStepIndex = -1
                    viewModel.navigationProgress.collect { progress ->
                        if (progress != null && viewModel.isNavigating.value) {
                            if (!viewModel.isRecalculating.value) {
                                renderNavigationStatus(progress)
                            }
                            if (progress.isArrived) {
                                stepsAdapter.setActiveStep(progress.currentStepIndex, 0)
                            } else {
                                stepsAdapter.setActiveStep(
                                    progress.currentStepIndex,
                                    progress.remainingDistanceToNextTurnMeters
                                )
                                if (progress.currentStepIndex != lastScrolledStepIndex) {
                                    lastScrolledStepIndex = progress.currentStepIndex
                                    binding.rvSteps.smoothScrollToPosition(progress.currentStepIndex)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.action_settings) {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
            return true
        }
        return false
    }

    private fun updateWatchStatusUI(state: WatchConnectionState, sendError: String?) {
        if (sendError != null) {
            binding.tvWatchStatus.text = getString(R.string.watch_status_error, sendError)
            binding.btnConnectWatch.isEnabled = true
            binding.btnConnectWatch.text = getString(R.string.retry)
            return
        }

        when (state) {
            is WatchConnectionState.Connecting -> {
                binding.tvWatchStatus.text = getString(R.string.watch_status_connecting)
                binding.btnConnectWatch.isEnabled = false
            }
            is WatchConnectionState.Connected -> {
                binding.tvWatchStatus.text = getString(R.string.watch_status_connected, state.deviceName)
                binding.btnConnectWatch.isEnabled = true
                binding.btnConnectWatch.text = getString(R.string.btn_connect_watch)
            }
            is WatchConnectionState.Disconnected -> {
                val reason = state.reason
                binding.tvWatchStatus.text = if (reason.isNullOrBlank()) {
                    getString(R.string.watch_status_disconnected)
                } else {
                    "${getString(R.string.watch_status_disconnected)} ($reason)"
                }
                binding.btnConnectWatch.isEnabled = true
                binding.btnConnectWatch.text = getString(R.string.btn_connect_watch)
            }
            is WatchConnectionState.Unauthorized -> {
                binding.tvWatchStatus.text = getString(R.string.watch_status_unauthorized)
                binding.btnConnectWatch.isEnabled = true
                binding.btnConnectWatch.text = getString(R.string.retry)
            }
            is WatchConnectionState.Error -> {
                binding.tvWatchStatus.text = getString(R.string.watch_status_error, state.message)
                binding.btnConnectWatch.isEnabled = true
                binding.btnConnectWatch.text = getString(R.string.retry)
            }
        }
    }

    private fun checkPermissionsAndFetchLocation() {
        val permissionsToRequest = mutableListOf<String>()

        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineLocationGranted) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (!coarseLocationGranted) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!notificationGranted) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (fineLocationGranted || coarseLocationGranted) {
            fetchDeviceLocation()
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
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

    override fun onResume() {
        super.onResume()
        viewModel.checkWatchConnection()
    }

    private fun toggleNavigation() {
        if (viewModel.isNavigating.value) {
            viewModel.stopNavigation()
            NavigationForegroundService.stop(this)
        } else {
            viewModel.startNavigation()
            if (viewModel.isNavigating.value) {
                NavigationForegroundService.start(this)
            }
        }
    }

    private fun updateNavigationUI(isNavigating: Boolean) {
        binding.rgTravelMode.isEnabled = !isNavigating
        for (i in 0 until binding.rgTravelMode.childCount) {
            binding.rgTravelMode.getChildAt(i).isEnabled = !isNavigating
        }

        if (isNavigating) {
            binding.tvStatus.text = getString(R.string.status_navigating)
            binding.btnNavigate.text = getString(R.string.btn_stop_navigation)
        } else {
            val isRouteLoaded = viewModel.routeState.value is RouteUiState.Success
            binding.tvStatus.text = if (isRouteLoaded) getString(R.string.status_route_ready) else getString(R.string.status_ready)
            binding.btnNavigate.text = getString(R.string.btn_start_navigation)
        }
    }

    private fun renderNavigationStatus(progress: NavigationProgress?) {
        if (progress == null) {
            binding.tvStatus.text = getString(R.string.status_navigating)
        } else if (progress.isArrived) {
            binding.tvStatus.text = getString(R.string.notification_arrived)
        } else {
            val distStr = DistanceFormatter.formatDistance(progress.remainingDistanceToNextTurnMeters)
            binding.tvStatus.text = getString(
                R.string.notification_turn_instruction,
                distStr,
                progress.currentStep.instruction
            )
        }
    }

    companion object {
        val DEFAULT_LOCATION = LatLng(21.028511, 105.854167)
    }
}
