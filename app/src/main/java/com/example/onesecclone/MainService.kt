package com.example.onesecclone

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.onesecclone.analytics.AnalyticsService
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class MainService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var isMonitoring = true
    private var lastDetectedApp: String? = null
    private var appSessionStartTime: Long = 0
    private var analyticsService: AnalyticsService? = null

    // Track which apps were just tapped (conscious opens)
    private val recentlyTappedApps = mutableMapOf<String, Long>()
    private val tapValidityWindow = 5000L // 5 seconds window after tap

    override fun onCreate() {
        super.onCreate()
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
        serviceScope.launch {
            while (isMonitoring) {
                if (isScreenOn()) {
                    val currentApp = getCurrentAppPackage()

                    // Record app usage analytics
                    recordAppUsage(currentApp)

                    if (currentApp == "com.instagram.android") {
                        // Record Instagram tap when first detected
                        if (lastDetectedApp != "com.instagram.android") {
                            recordInstagramTap()
                        }

                        val currentTime = System.currentTimeMillis()
                        val timeSinceLastSkip = currentTime - OverlayService.getLastSkipTime()

                        if (!isServiceRunning(OverlayService::class.java) &&
                            timeSinceLastSkip >= OverlayService.COOLDOWN_PERIOD) {
                            val overlayIntent = Intent(this@MainService, OverlayService::class.java)
                            startService(overlayIntent)
                        }
                    } else if (currentApp == "com.facebook.katana") {
                        // Record Facebook tap when first detected
                        if (lastDetectedApp != "com.facebook.katana") {
                            recordFacebookTap()
                        }
                    } else if (currentApp == "com.google.android.youtube") {
                        // Record YouTube tap when first detected
                        if (lastDetectedApp != "com.google.android.youtube") {
                            recordYouTubeTap()
                        }
                    }

                    lastDetectedApp = currentApp
                }
                delay(1000)
            }
        }
    }

    private fun recordAppUsage(currentApp: String) {
        // Define the apps we want to track for research
        val targetApps = setOf(
            "com.instagram.android",
            "com.facebook.katana",
            "com.google.android.youtube"
        )

        val currentTime = System.currentTimeMillis()

        // Only record sessions for our target apps AND only if they were recently tapped
        if (currentApp.isNotEmpty() && currentApp != lastDetectedApp && targetApps.contains(currentApp)) {

            // Check if this app was recently tapped (indicating conscious opening)
            val tapTime = recentlyTappedApps[currentApp]
            val wasRecentlyTapped = tapTime != null && (currentTime - tapTime) <= tapValidityWindow

            if (wasRecentlyTapped) {
                // End previous session if it was also a target app
                if (lastDetectedApp != null && targetApps.contains(lastDetectedApp!!) && appSessionStartTime > 0) {
                    recordAppSession(lastDetectedApp!!, appSessionStartTime, currentTime)
                }

                // Start new session for consciously opened target app
                appSessionStartTime = currentTime

                // Remove the tap record since we've used it
                recentlyTappedApps.remove(currentApp)

                Log.d("MainService", "Started conscious session for $currentApp")
            } else {
                Log.d("MainService", "Ignoring unconscious activation of $currentApp (no recent tap)")
            }

        } else if (lastDetectedApp != null && targetApps.contains(lastDetectedApp!!) && !targetApps.contains(currentApp)) {
            // End session when leaving a target app (switching to non-target app)
            // Record the session regardless of duration - even 1-second conscious usage is valuable
            if (appSessionStartTime > 0) {
                val sessionDuration = currentTime - appSessionStartTime
                recordAppSession(lastDetectedApp!!, appSessionStartTime, currentTime)
                Log.d("MainService", "Ended conscious session for ${lastDetectedApp!!} (${sessionDuration}ms)")
                appSessionStartTime = 0 // Reset session start time
            }
        }

        // Clean up old tap records (older than validity window)
        recentlyTappedApps.entries.removeAll { (_, tapTime) ->
            (currentTime - tapTime) > tapValidityWindow
        }
    }

    private fun recordAppSession(packageName: String, startTime: Long, endTime: Long) {
        try {
            // Get app name from package
            val appName = try {
                val packageInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(packageInfo).toString()
            } catch (e: Exception) {
                packageName
            }

            // Use the static method to record the session
            AnalyticsService.recordAppSessionStatic(appName, packageName, startTime, endTime)

            Log.d("MainService", "Recorded app session: $appName ($packageName)")
        } catch (e: Exception) {
            Log.e("MainService", "Error recording app session", e)
        }
    }

    private fun recordInstagramTap() {
        try {
            // Record the tap time for session validation
            recentlyTappedApps["com.instagram.android"] = System.currentTimeMillis()

            // Record the tap event for analytics
            AnalyticsService.recordAppTapStatic("Instagram", "com.instagram.android")

            Log.d("MainService", "Recorded Instagram tap")
        } catch (e: Exception) {
            Log.e("MainService", "Error recording Instagram tap", e)
        }
    }

    private fun recordFacebookTap() {
        try {
            recentlyTappedApps["com.facebook.katana"] = System.currentTimeMillis()
            AnalyticsService.recordAppTapStatic("Facebook", "com.facebook.katana")
            Log.d("MainService", "Recorded Facebook tap")
        } catch (e: Exception) {
            Log.e("MainService", "Error recording Facebook tap", e)
        }
    }

    private fun recordYouTubeTap() {
        try {
            recentlyTappedApps["com.google.android.youtube"] = System.currentTimeMillis()
            AnalyticsService.recordAppTapStatic("YouTube", "com.google.android.youtube")
            Log.d("MainService", "Recorded YouTube tap")
        } catch (e: Exception) {
            Log.e("MainService", "Error recording YouTube tap", e)
        }
    }

    private fun isScreenOn(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isInteractive
    }

    private fun getCurrentAppPackage(): String {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.MILLISECONDS.convert(1, TimeUnit.SECONDS)

        val queryUsageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )

        return queryUsageStats
            ?.maxByOrNull { it.lastTimeUsed }
            ?.packageName ?: ""
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == serviceClass.name }
    }

    private fun startOverlayService() {
        val overlayIntent = Intent(this, OverlayService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(overlayIntent)
        } else {
            startService(overlayIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isMonitoring = false
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
