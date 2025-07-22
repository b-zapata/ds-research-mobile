package com.example.onesecclone

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.onesecclone.usage.UsageSessionTracker
import kotlinx.coroutines.*

class MainService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var usageSessionTracker: UsageSessionTracker

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "MainService"

        // Singleton pattern to prevent multiple monitoring loops
        @Volatile
        private var isMonitoring = false
        private var lastForegroundApp: String? = null

        // Thread-safe synchronization
        private val monitoringLock = Any()

        // Target apps for overlay intervention
        private val OVERLAY_TARGET_APPS = setOf(
            "com.instagram.android"
            // Add other apps here if you want overlays for them too
        )
    }

    override fun onCreate() {
        super.onCreate()
        usageSessionTracker = UsageSessionTracker(this)
        startForeground()
    }

    private fun startForeground() {
        val channelId = createNotificationChannel()
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("App Monitor")
            .setContentText("Monitoring is active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "MainServiceChannel"
            val channelName = "Main Service Channel"
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            return channelId
        }
        return ""
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        synchronized(monitoringLock) {
            if (isMonitoring) {
                Log.d(TAG, "Monitoring is already active, skipping start")
                return
            }

            // Check if we have usage access permission
            if (!usageSessionTracker.hasUsageAccessPermission()) {
                Log.w(TAG, "Usage access permission not granted - session tracking may not work properly")
            }

            isMonitoring = true
        }

        serviceScope.launch {
            Log.d(TAG, "Starting usage session monitoring")
            while (isMonitoring) {
                try {
                    if (isScreenOn()) {
                        // Check for new app sessions using Android's built-in data
                        usageSessionTracker.checkForNewSessions()

                        // Check if we need to show overlay for current app
                        val currentForegroundApp = usageSessionTracker.getCurrentForegroundApp()
                        handleOverlayLogic(currentForegroundApp)

                        lastForegroundApp = currentForegroundApp
                    } else {
                        Log.d(TAG, "Screen is off, skipping monitoring cycle")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in monitoring cycle", e)
                }

                // Check every 10 seconds instead of every second - much more efficient
                delay(10000)
            }
            Log.d(TAG, "Usage session monitoring ended")
        }
    }

    /**
     * Handle overlay logic for intervention apps
     */
    private fun handleOverlayLogic(currentApp: String?) {
        if (currentApp != null && OVERLAY_TARGET_APPS.contains(currentApp)) {
            // Only show overlay if app changed to a target app
            if (lastForegroundApp != currentApp) {
                Log.d(TAG, "Target app opened: $currentApp")

                val currentTime = System.currentTimeMillis()
                val timeSinceLastSkip = currentTime - OverlayService.getLastSkipTime()

                if (!isServiceRunning(OverlayService::class.java) &&
                    timeSinceLastSkip >= OverlayService.COOLDOWN_PERIOD) {
                    val overlayIntent = Intent(this@MainService, OverlayService::class.java)
                    startService(overlayIntent)
                    Log.d(TAG, "Started overlay service for $currentApp")
                } else {
                    Log.d(TAG, "Overlay service already running or in cooldown period")
                }
            }
        }
    }

    private fun isScreenOn(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isInteractive
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == serviceClass.name }
    }

    override fun onDestroy() {
        super.onDestroy()
        synchronized(monitoringLock) {
            isMonitoring = false
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent): IBinder? = null
}
