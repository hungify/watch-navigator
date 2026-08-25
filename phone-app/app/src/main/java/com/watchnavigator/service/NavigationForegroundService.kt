package com.watchnavigator.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.watchnavigator.MainActivity
import com.watchnavigator.R
import com.watchnavigator.engine.LocationRequestConfig
import com.watchnavigator.engine.NavigationProgress
import com.watchnavigator.engine.NavigationSessionManager
import com.watchnavigator.model.LatLng
import com.watchnavigator.util.DistanceFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NavigationForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var notificationManager: NotificationManager
    private lateinit var sessionManager: NavigationSessionManager

    private var progressCollectorJob: Job? = null
    private var isTracking = false

    override fun onCreate() {
        super.onCreate()
        sessionManager = NavigationSessionManager.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel()

        locationCallback =
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    for (location in result.locations) {
                        val latLng = LatLng(location.latitude, location.longitude)
                        sessionManager.onLocationUpdate(latLng)
                    }
                }
            }

        observeNavigationProgress()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_STOP_NAVIGATION -> {
                stopNavigationService()
                return START_NOT_STICKY
            }
            ACTION_START_NAVIGATION -> {
                startNavigationTracking()
                return START_STICKY
            }
            else -> {
                if (sessionManager.isNavigating.value) {
                    startNavigationTracking()
                    return START_STICKY
                } else {
                    stopNavigationService()
                    return START_NOT_STICKY
                }
            }
        }
    }

    private fun startNavigationTracking() {
        if (isTracking) return
        if (!sessionManager.isNavigating.value) {
            stopNavigationService()
            return
        }
        val initialNotification = buildNotification(sessionManager.navigationProgress.value)
        val foregroundServiceType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            }

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                initialNotification,
                foregroundServiceType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            stopNavigationService()
            return
        }

        val travelMode = sessionManager.activeTravelMode.value
        val locationRequest = LocationRequestConfig.createLocationRequest(travelMode)

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            isTracking = true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException requesting location updates", e)
            stopNavigationService()
        } catch (e: Exception) {
            Log.e(TAG, "Exception requesting location updates", e)
            stopNavigationService()
        }
    }

    private fun stopNavigationService() {
        isTracking = false
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            // Ignore if already removed
        }
        sessionManager.stopSession()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun observeNavigationProgress() {
        progressCollectorJob?.cancel()
        progressCollectorJob =
            serviceScope.launch {
                sessionManager.navigationProgress.collect { progress ->
                    if (isTracking) {
                        val updatedNotification = buildNotification(progress)
                        notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                    }
                }
            }

        serviceScope.launch {
            sessionManager.isRecalculating.collect { isRecalculating ->
                if (isTracking) {
                    val updatedNotification = buildNotification(sessionManager.navigationProgress.value)
                    notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                }
            }
        }

        serviceScope.launch {
            sessionManager.activeRoute.collect {
                if (isTracking) {
                    val updatedNotification = buildNotification(sessionManager.navigationProgress.value)
                    notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                }
            }
        }

        serviceScope.launch {
            sessionManager.isNavigating.collect { isNavigating ->
                if (!isNavigating && isTracking) {
                    stopNavigationService()
                }
            }
        }
    }

    private fun buildNotification(progress: NavigationProgress?): Notification {
        val destAddress = sessionManager.activeRoute.value?.destinationAddress ?: getString(R.string.title_destination)

        val title = getString(R.string.notification_navigating_to, destAddress)
        val contentText =
            when {
                sessionManager.isRecalculating.value -> getString(R.string.notification_recalculating)
                progress == null -> getString(R.string.notification_starting)
                progress.isArrived -> getString(R.string.notification_arrived)
                else -> {
                    val distStr = DistanceFormatter.formatDistance(progress.remainingDistanceToNextTurnMeters)
                    val instruction = progress.currentStep.instruction
                    getString(R.string.notification_turn_instruction, distStr, instruction)
                }
            }

        val openAppIntent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val openAppPendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        val stopIntent =
            Intent(this, NavigationForegroundService::class.java).apply {
                action = ACTION_STOP_NAVIGATION
            }
        val stopPendingIntent =
            PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        return NotificationCompat
            .Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                getString(R.string.notification_action_stop),
                stopPendingIntent
            ).setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notification_channel_description)
                    setShowBadge(false)
                }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        progressCollectorJob?.cancel()
        serviceScope.cancel()
        if (isTracking) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            } catch (e: Exception) {
                // Ignore
            }
            isTracking = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "NavForegroundService"
        const val NOTIFICATION_CHANNEL_ID = "watch_navigator_navigation_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_NAVIGATION = "com.watchnavigator.action.START_NAVIGATION"
        const val ACTION_STOP_NAVIGATION = "com.watchnavigator.action.STOP_NAVIGATION"

        fun start(context: Context) {
            try {
                val intent =
                    Intent(context, NavigationForegroundService::class.java).apply {
                        action = ACTION_START_NAVIGATION
                    }
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent =
                    Intent(context, NavigationForegroundService::class.java).apply {
                        action = ACTION_STOP_NAVIGATION
                    }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop foreground service", e)
            }
        }
    }
}
