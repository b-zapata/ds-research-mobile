package com.example.onesecclone.analytics

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.*
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit

class AnalyticsService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var hourlyJob: Job? = null
    private var dailyJob: Job? = null
    private val appSessions = mutableMapOf<String, MutableList<AnalyticsData.AppSession>>()
    private val appTaps = mutableMapOf<String, MutableList<AnalyticsData.AppTap>>()
    private val interventions = mutableMapOf<String, MutableList<AnalyticsData.Intervention>>()

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        startDataCollection()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startDataCollection() {
        // Start hourly device status collection
        hourlyJob = serviceScope.launch {
            while (isActive) {
                collectAndSendDeviceStatus()
                delay(TimeUnit.HOURS.toMillis(1))
            }
        }

        // Schedule daily summary at 4 AM
        scheduleDailySummary()
    }

    private fun scheduleDailySummary() {
        // Using AlarmManager instead of direct Java 8 Time API for better compatibility
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AnalyticsService::class.java).apply {
            action = ACTION_DAILY_SUMMARY
        }

        // Create a unique request code based on the action
        val requestCode = ACTION_DAILY_SUMMARY.hashCode()

        // Create a pending intent that will trigger our daily summary
        val pendingIntent = PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Calculate time for 4 AM tomorrow
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)

            // If it's already past 4 AM, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Check if we can schedule exact alarms on Android 12+
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ requires special permission check
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    // Fallback to inexact alarm if exact alarms are not allowed
                    alarmManager.setInexactRepeating(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        AlarmManager.INTERVAL_DAY,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // If we can't schedule exact alarms, use inexact as fallback
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        }

        // Register a broadcast receiver to handle our daily summary action
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dailySummaryReceiver, IntentFilter(ACTION_DAILY_SUMMARY), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(dailySummaryReceiver, IntentFilter(ACTION_DAILY_SUMMARY))
        }
    }

    private val dailySummaryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_DAILY_SUMMARY) {
                serviceScope.launch {
                    generateAndSendDailySummary()
                    // Reschedule for the next day
                    scheduleDailySummary()
                }
            }
        }
    }

    fun recordAppSession(appName: String, packageName: String, startTime: Long, endTime: Long) {
        try {
            val sessionStart = ZonedDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(startTime),
                ZoneId.systemDefault()
            )
            val sessionEnd = ZonedDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(endTime),
                ZoneId.systemDefault()
            )

            val session = AnalyticsData.AppSession(
                appName = appName,
                packageName = packageName,
                sessionStart = sessionStart,
                sessionEnd = sessionEnd
            )
            appSessions.getOrPut(packageName) { mutableListOf() }.add(session)

            // Send immediately for real-time tracking
            serviceScope.launch {
                sendDataToServer(session)
            }
        } catch (e: Exception) {
            println("Error recording app session: ${e.message}")
        }
    }

    fun recordAppTap(appName: String, packageName: String) {
        try {
            // Using the desugared time API with proper handling and error catching
            val tap = AnalyticsData.AppTap(
                timestamp = ZonedDateTime.now(),
                appName = appName,
                packageName = packageName
            )
            appTaps.getOrPut(packageName) { mutableListOf() }.add(tap)

            // Launch coroutine to call suspend function
            serviceScope.launch {
                sendDataToServer(tap)
            }
        } catch (e: Exception) {
            println("Error recording app tap: ${e.message}")
        }
    }

    fun recordIntervention(
        appName: String,
        interventionType: String,
        videoDuration: Int? = null,
        requiredWatchTime: Int? = null,
        buttonClicked: String
    ) {
        try {
            // Using the desugared time API with proper handling and error catching
            val currentTime = ZonedDateTime.now()
            val intervention = AnalyticsData.Intervention(
                interventionStart = currentTime,
                interventionEnd = currentTime,
                appName = appName,
                interventionType = interventionType,
                videoDuration = videoDuration,
                requiredWatchTime = requiredWatchTime,
                buttonClicked = buttonClicked
            )
            interventions.getOrPut(appName) { mutableListOf() }.add(intervention)

            // Launch coroutine to call suspend function
            serviceScope.launch {
                sendDataToServer(intervention)
            }
        } catch (e: Exception) {
            println("Error recording intervention: ${e.message}")
        }
    }

    private fun collectAndSendDeviceStatus() {
        try {
            val deviceStatus = AnalyticsData.DeviceStatus(
                batteryLevel = getBatteryLevel(),
                isCharging = isDeviceCharging(),
                connectionType = getConnectionType(),
                connectionStrength = getConnectionStrength(),
                appVersion = getAppVersion(),
                lastBatchSent = ZonedDateTime.now()
            )
            serviceScope.launch {
                sendDataToServer(deviceStatus)
            }
        } catch (e: Exception) {
            println("Error collecting device status: ${e.message}")
        }
    }

    private fun generateAndSendDailySummary() {
        try {
            val appTotals = mutableMapOf<String, AnalyticsData.AppStats>()

            // Calculate totals for each app
            appSessions.forEach { (packageName, sessions) ->
                val appName = getAppNameFromPackage(packageName)
                val taps = appTaps[packageName]?.size ?: 0
                val appInterventions = interventions[appName] ?: emptyList()

                appTotals[appName] = AnalyticsData.AppStats(
                    minutes = calculateTotalMinutes(sessions),
                    sessions = sessions.size,
                    totalTaps = taps,
                    totalDelays = appInterventions.count { it.interventionType == "delay" },
                    totalAbandonments = appInterventions.count { it.buttonClicked == "Close app" },
                    totalInterruptions = appInterventions.size
                )
            }

            // Use a safer approach for getting current date
            val today = try {
                LocalDate.now().toString()
            } catch (e: Exception) {
                // Fallback to Java Calendar if needed
                val calendar = Calendar.getInstance()
                String.format("%d-%02d-%02d",
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH))
            }

            val summary = AnalyticsData.DailySummary(
                date = today,
                totalScreenTime = calculateTotalScreenTime(),
                appTotals = appTotals
            )

            serviceScope.launch {
                sendDataToServer(summary)
            }

            // Clear daily data after sending
            clearDailyData()
        } catch (e: Exception) {
            println("Error generating daily summary: ${e.message}")
        }
    }

    private fun calculateTotalMinutes(sessions: List<AnalyticsData.AppSession>): Int {
        return sessions.sumOf { session ->
            val minutes = ChronoUnit.MINUTES.between(session.sessionStart, session.sessionEnd)
            minutes.toInt()
        }
    }

    private fun calculateTotalScreenTime(): Int {
        return appSessions.values.flatten().sumOf { session ->
            val minutes = ChronoUnit.MINUTES.between(session.sessionStart, session.sessionEnd)
            minutes.toInt()
        }
    }

    private fun getBatteryLevel(): Int {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun isDeviceCharging(): Boolean {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        return bm.isCharging
    }

    private fun getConnectionType(): String {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
        return when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
            else -> "none"
        }
    }

    private fun getConnectionStrength(): String {
        // This is a simplified version. You might want to implement more sophisticated signal strength detection
        return "strong" // Return a non-null string value
    }

    private fun getAppVersion(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            "unknown"
        }
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            val packageInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(packageInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun clearDailyData() {
        appSessions.clear()
        appTaps.clear()
        interventions.clear()
    }

    private fun sendDataToServer(data: AnalyticsData) {
        // TODO: Implement actual server communication
        // For now, we'll log the data with detailed Android logging
        android.util.Log.i("AnalyticsService", "📊 ANALYTICS DATA RECORDED:")
        when (data) {
            is AnalyticsData.AppSession -> {
                android.util.Log.i("AnalyticsService", "📱 APP SESSION: ${data.appName} (${data.packageName})")
                android.util.Log.i("AnalyticsService", "   ⏰ Duration: ${ChronoUnit.MINUTES.between(data.sessionStart, data.sessionEnd)} minutes")
            }
            is AnalyticsData.AppTap -> {
                android.util.Log.i("AnalyticsService", "👆 APP TAP: ${data.appName} at ${data.timestamp}")
            }
            is AnalyticsData.Intervention -> {
                android.util.Log.i("AnalyticsService", "🎬 INTERVENTION: ${data.appName} - ${data.buttonClicked}")
                android.util.Log.i("AnalyticsService", "   📺 Video: ${data.videoDuration}s, Required: ${data.requiredWatchTime}s")
            }
            is AnalyticsData.DeviceStatus -> {
                android.util.Log.i("AnalyticsService", "🔋 DEVICE STATUS: Battery ${data.batteryLevel}%, ${data.connectionType}")
            }
            is AnalyticsData.DailySummary -> {
                android.util.Log.i("AnalyticsService", "📊 DAILY SUMMARY: ${data.date}")
                android.util.Log.i("AnalyticsService", "   📱 Total screen time: ${data.totalScreenTime} minutes")
                android.util.Log.i("AnalyticsService", "   📋 Apps tracked: ${data.appTotals.size}")
            }
        }
        println("Ready to send data: $data")
    }

    override fun onDestroy() {
        super.onDestroy()
        hourlyJob?.cancel()
        dailyJob?.cancel()
        serviceScope.cancel()
        INSTANCE = null
    }

    companion object {
        const val ACTION_DAILY_SUMMARY = "com.example.onesecclone.ACTION_DAILY_SUMMARY"

        @Volatile
        private var INSTANCE: AnalyticsService? = null

        fun getInstance(): AnalyticsService? = INSTANCE

        // Static methods for easy access from other services
        fun recordAppSessionStatic(appName: String, packageName: String, startTime: Long, endTime: Long) {
            getInstance()?.recordAppSession(appName, packageName, startTime, endTime)
        }

        fun recordAppTapStatic(appName: String, packageName: String) {
            getInstance()?.recordAppTap(appName, packageName)
        }

        fun recordInterventionStatic(
            appName: String,
            interventionType: String,
            videoDuration: Int? = null,
            requiredWatchTime: Int? = null,
            buttonClicked: String
        ) {
            getInstance()?.recordIntervention(appName, interventionType, videoDuration, requiredWatchTime, buttonClicked)
        }

        // Debug method to check current analytics counts
        fun getAnalyticsCounts(): String {
            val instance = getInstance()
            return if (instance != null) {
                val sessionCount = instance.appSessions.values.sumOf { it.size }
                val tapCount = instance.appTaps.values.sumOf { it.size }
                val interventionCount = instance.interventions.values.sumOf { it.size }
                "📊 Analytics Status: $sessionCount sessions, $tapCount taps, $interventionCount interventions"
            } else {
                "❌ AnalyticsService not running"
            }
        }

        // Public methods to access analytics data for UI display
        fun getSessionCount(): Int = getInstance()?.appSessions?.values?.sumOf { it.size } ?: 0
        fun getTapCount(): Int = getInstance()?.appTaps?.values?.sumOf { it.size } ?: 0
        fun getInterventionCount(): Int = getInstance()?.interventions?.values?.sumOf { it.size } ?: 0

        fun getRecentSessions(limit: Int = 5): List<AnalyticsData.AppSession> {
            return getInstance()?.appSessions?.values?.flatten()?.takeLast(limit) ?: emptyList()
        }

        fun getRecentTaps(limit: Int = 5): List<AnalyticsData.AppTap> {
            return getInstance()?.appTaps?.values?.flatten()?.takeLast(limit) ?: emptyList()
        }

        fun getRecentInterventions(limit: Int = 5): List<AnalyticsData.Intervention> {
            return getInstance()?.interventions?.values?.flatten()?.takeLast(limit) ?: emptyList()
        }
    }
}
