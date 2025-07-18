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
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.onesecclone.network.DataSyncService
import kotlinx.coroutines.*
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit

class AnalyticsService : Service() {

    companion object {
        private const val TAG = "AnalyticsService"
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

        // Method to get all data for filtering by date range
        fun getAllSessions(): List<AnalyticsData.AppSession> {
            return getInstance()?.appSessions?.values?.flatten() ?: emptyList()
        }

        fun getAllTaps(): List<AnalyticsData.AppTap> {
            return getInstance()?.appTaps?.values?.flatten() ?: emptyList()
        }

        fun getAllInterventions(): List<AnalyticsData.Intervention> {
            return getInstance()?.interventions?.values?.flatten() ?: emptyList()
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var hourlyJob: Job? = null
    private var dailyJob: Job? = null
    private val appSessions = mutableMapOf<String, MutableList<AnalyticsData.AppSession>>()
    private val appTaps = mutableMapOf<String, MutableList<AnalyticsData.AppTap>>()
    private val interventions = mutableMapOf<String, MutableList<AnalyticsData.Intervention>>()

    private lateinit var dataSyncService: DataSyncService

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        dataSyncService = DataSyncService.getInstance(this)
        startDataCollection()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startDataCollection() {
        // Start hourly batch data collection and sending
        hourlyJob = serviceScope.launch {
            while (isActive) {
                collectAndSendHourlyBatch()
                delay(TimeUnit.HOURS.toMillis(1))
            }
        }

        // Schedule daily summary at 4 AM
        scheduleDailySummary()
    }

    private fun scheduleDailySummary() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AnalyticsService::class.java).apply {
            action = ACTION_DAILY_SUMMARY
        }

        val requestCode = ACTION_DAILY_SUMMARY.hashCode()

        val pendingIntent = PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
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
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        }

        // Use ContextCompat to handle RECEIVER_NOT_EXPORTED flag across all Android versions
        ContextCompat.registerReceiver(
            this,
            dailySummaryReceiver,
            IntentFilter(ACTION_DAILY_SUMMARY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private val dailySummaryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_DAILY_SUMMARY) {
                serviceScope.launch {
                    generateAndSendDailySummary()
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
            Log.d(TAG, "Recorded app session for $appName (will be sent in next hourly batch)")
        } catch (e: Exception) {
            Log.e(TAG, "Error recording app session: ${e.message}")
        }
    }

    fun recordAppTap(appName: String, packageName: String) {
        try {
            val tap = AnalyticsData.AppTap(
                timestamp = ZonedDateTime.now(),
                appName = appName,
                packageName = packageName
            )
            appTaps.getOrPut(packageName) { mutableListOf() }.add(tap)
            Log.d(TAG, "Recorded app tap for $appName (will be sent in next hourly batch)")
        } catch (e: Exception) {
            Log.e(TAG, "Error recording app tap: ${e.message}")
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
            Log.d(TAG, "Recorded intervention for $appName (will be sent in next hourly batch)")
        } catch (e: Exception) {
            Log.e(TAG, "Error recording intervention: ${e.message}")
        }
    }

    private fun collectAndSendHourlyBatch() {
        try {
            Log.d(TAG, "Starting hourly batch collection...")

            // Collect device status
            val deviceStatus = AnalyticsData.DeviceStatus(
                batteryLevel = getBatteryLevel(),
                isCharging = isDeviceCharging(),
                connectionType = getConnectionType(),
                connectionStrength = getConnectionStrength(),
                appVersion = getAppVersion(),
                lastBatchSent = ZonedDateTime.now()
            )

            // Combine all analytics data into a single list
            val allData = mutableListOf<AnalyticsData>()

            // Add device status
            allData.add(deviceStatus)

            // Add all accumulated app sessions, taps, and interventions
            allData.addAll(appSessions.values.flatten())
            allData.addAll(appTaps.values.flatten())
            allData.addAll(interventions.values.flatten())

            if (allData.size > 1) { // More than just device status
                Log.d(TAG, "Sending batch with ${allData.size} items: ${appSessions.values.flatten().size} sessions, ${appTaps.values.flatten().size} taps, ${interventions.values.flatten().size} interventions, 1 device status")

                serviceScope.launch {
                    val success = dataSyncService.sendBatchData(allData)
                    if (success) {
                        Log.d(TAG, "Successfully sent hourly batch to server")
                        // Clear the sent data (but keep device status for next batch)
                        appSessions.clear()
                        appTaps.clear()
                        interventions.clear()
                    } else {
                        Log.w(TAG, "Failed to send hourly batch, data will be retried")
                    }
                }
            } else {
                Log.d(TAG, "Only device status to send, sending individually")
                serviceScope.launch {
                    sendDataToServer(deviceStatus)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting hourly batch: ${e.message}", e)
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
            Log.e(TAG, "Error collecting device status: ${e.message}")
        }
    }

    private fun generateAndSendDailySummary() {
        try {
            val appTotals = mutableMapOf<String, AnalyticsData.AppStats>()

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

            val today = try {
                LocalDate.now().toString()
            } catch (e: Exception) {
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

            clearDailyData()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating daily summary: ${e.message}")
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

    private suspend fun sendDataToServer(data: AnalyticsData) {
        try {
            val success = dataSyncService.sendData(data)
            if (success) {
                Log.d(TAG, "Successfully sent ${data::class.simpleName} to server")
            } else {
                Log.w(TAG, "Failed to send ${data::class.simpleName}, queued for offline sync")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending ${data::class.simpleName}: ${e.message}", e)
        }
    }

    private fun clearDailyData() {
        appSessions.clear()
        appTaps.clear()
        interventions.clear()
        Log.d(TAG, "Cleared daily analytics data")
    }

    private fun getBatteryLevel(): Int {
        return try {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting battery level: ${e.message}")
            -1
        }
    }

    private fun isDeviceCharging(): Boolean {
        return try {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.isCharging
        } catch (e: Exception) {
            Log.e(TAG, "Error checking charging status: ${e.message}")
            false
        }
    }

    private fun getConnectionType(): String {
        return try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)

            when {
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ethernet"
                else -> "none"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting connection type: ${e.message}")
            "unknown"
        }
    }

    private fun getConnectionStrength(): String {
        return try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)

            when {
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "good"
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "fair"
                else -> "poor"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting connection strength: ${e.message}")
            "unknown"
        }
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app version: ${e.message}")
            "unknown"
        }
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            val packageManager = this.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app name for package $packageName: ${e.message}")
            packageName
        }
    }

    fun sendBatchData() {
        serviceScope.launch {
            try {
                // Combine all analytics data into a single list
                val allData = mutableListOf<com.example.onesecclone.analytics.AnalyticsData>()
                allData.addAll(appSessions.values.flatten())
                allData.addAll(appTaps.values.flatten())
                allData.addAll(interventions.values.flatten())

                // Pass the list directly to sendBatchData method
                val success = dataSyncService.sendBatchData(allData)
                if (success) {
                    Log.d(TAG, "Successfully sent batch data to server")
                    clearDailyData()
                } else {
                    Log.w(TAG, "Failed to send batch data")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending batch data: ${e.message}", e)
            }
        }
    }

    fun getOfflineQueueSize(): Int = dataSyncService.getQueueSize()

    override fun onDestroy() {
        super.onDestroy()
        try {
            hourlyJob?.cancel()
            dailyJob?.cancel()
            serviceScope.cancel()
            unregisterReceiver(dailySummaryReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error during service destruction: ${e.message}")
        }
        INSTANCE = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DAILY_SUMMARY -> {
                serviceScope.launch {
                    generateAndSendDailySummary()
                }
            }
        }
        return START_STICKY
    }
}
