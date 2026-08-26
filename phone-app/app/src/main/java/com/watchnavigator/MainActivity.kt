package com.watchnavigator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.watchnavigator.data.GoogleDirectionsService
import com.watchnavigator.data.GooglePlacesSearchService
import com.watchnavigator.data.HuaweiWearEngineService
import com.watchnavigator.data.SharedPreferencesRepository
import com.watchnavigator.model.LatLng
import com.watchnavigator.service.NavigationForegroundService
import com.watchnavigator.ui.MainScreen
import com.watchnavigator.ui.MainViewModel
import com.watchnavigator.ui.SettingsActivity
import com.watchnavigator.ui.theme.WatchNavigatorTheme

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var placesClient: PlacesClient? = null

    private val viewModel: MainViewModel by viewModels {
        val placesSearchService = GooglePlacesSearchService(placesClient)
        val directionsService =
            GoogleDirectionsService(
                apiKey = BuildConfig.MAPS_API_KEY,
                serverUrl = BuildConfig.NAV_SERVER_URL,
                serverToken = BuildConfig.NAV_SERVER_TOKEN
            )
        val wearEngineService = HuaweiWearEngineService(applicationContext)
        val preferencesRepository = SharedPreferencesRepository(applicationContext)
        MainViewModel.Factory(placesSearchService, directionsService, wearEngineService, preferencesRepository)
    }

    private val permissionsLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            if (fineLocationGranted || coarseLocationGranted) {
                fetchDeviceLocation()
            }
        }

    private val settingsLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            viewModel.refreshTravelModeFromPreferences()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initPlacesSdk()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            WatchNavigatorTheme {
                MainScreen(
                    viewModel = viewModel,
                    onOpenSettings = {
                        settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
                    },
                    onStartNavigationService = {
                        NavigationForegroundService.start(this)
                    },
                    onStopNavigationService = {
                        NavigationForegroundService.stop(this)
                    }
                )
            }
        }

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

    private fun checkPermissionsAndFetchLocation() {
        val permissionsToRequest = mutableListOf<String>()

        val fineLocationGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted =
            ContextCompat.checkSelfPermission(
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
            val notificationGranted =
                ContextCompat.checkSelfPermission(
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
            fusedLocationClient
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
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
        viewModel.refreshTravelModeFromPreferences()
    }

    companion object {
        val DEFAULT_LOCATION = LatLng(21.028511, 105.854167)
    }
}
