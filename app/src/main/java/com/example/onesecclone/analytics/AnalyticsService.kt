package com.example.onesecclone.analytics

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.onesecclone.network.DataSyncService
import kotlinx.coroutines.*
import java.time.*
import java.time.temporal.ChronoUnit
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
            buttonClicked: String,
            interventionStartTime: Long? = null
        ) {
            getInstance()?.recordIntervention(appName, interventionType, videoDuration, requiredWatchTime, buttonClicked, interventionStartTime)
        }

        // Debug method to check current analytics counts
        fun getAnalyticsCounts(): String {
            val instance = getInstance()
            return if (instance != null) {
                synchronized(instance.dataLock) {
                    val sessionCount = instance.appSessions.values.sumOf { it.size }
                    val tapCount = instance.appTaps.values.sumOf { it.size }
                    val interventionCount = instance.interventions.values.sumOf { it.size }
                    "📊 Analytics Status: $sessionCount sessions, $tapCount taps, $interventionCount interventions"
                }
            } else {
                "❌ AnalyticsService not running"
            }
        }

        // Public methods to access analytics data for UI display
        fun getSessionCount(): Int {
            val instance = getInstance()
            return if (instance != null) {
                synchronized(instance.dataLock) {
                    instance.appSessions.values.sumOf { it.size }
                }
            } else 0
        }

        fun getTapCount(): Int {
            val instance = getInstance()
            return if (instance != null) {
                synchronized(instance.dataLock) {
                    instance.appTaps.values.sumOf { it.size }
                }
            } else 0
        }

        fun getInterventionCount(): Int {
            val instance = getInstance()
            return if (instance != null) {
                synchronized(instance.dataLock) {
                    instance.interventions.values.sumOf { it.size }
                }
            } else 0
        }

        fun getRecentSessions(limit: Int = 5): List<AnalyticsData.AppSession> {
            val instance = getInstance()
            return if (instance != null) {
                synchronized(instance.dataLock) {
                    instance.appSessions.values.flatten().takeLast(limit)
                }
            } else emptyList()
        }

        fun getRecentTaps(limit: Int = 5): List<AnalyticsData.AppTap> {
            val instance = getInstance()
            return if (instance != null) {
                synchronized(instance.dataLock) {
                    instance.appTaps.values.flatten().takeLast(limit)
                }
            } else emptyList()
        }

        fun getRecentInterventions(limit: Int = 5): List<AnalyticsData.Intervention> {
            val instance = getInstance()
            return if (instance != null) {
                synchronized(instance.dataLock) {
                    instance.interventions.values.flatten().takeLast(limit)
                }
            } else emptyList()
        }

        // Method to get all data for filtering by date range
        fun getAllSessions(): List<AnalyticsData.AppSession> {
            val instance = getInstance()
            return if (instance != null) {
                synchronized(instance.dataLock) {
                    instance.appSessions.values.flatten()
                }
            } else emptyList()
        }

        fun getAllTaps(): List<AnalyticsData.AppTap> {
            val instance = getInstance()
            return if (instance != null) {
                synchronized(instance.dataLock) {
                    instance.appTaps.values.flatten()
                }
            } else emptyList()
        }

        fun getAllInterventions(): List<AnalyticsData.Intervention> {
            val instance = getInstance()
            return if (instance != null) {
                synchronized(instance.dataLock) {
                    instance.interventions.values.flatten()
                }
            } else emptyList()
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var hourlyJob: Job? = null
    private var dailyJob: Job? = null

    // Thread-safe collections to prevent race conditions from multiple MainService instances
    val dataLock = Any()
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
            while (true) {
                delay(TimeUnit.HOURS.toMillis(1)) // Wait 1 hour
                try {
                    sendBatchData()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in hourly data collection: ${e.message}")
                }
            }
        }

        // Start daily summary reporting
        dailyJob = serviceScope.launch {
            val now = LocalDateTime.now()
            val tomorrow = now.plusDays(1).withHour(0).withMinute(0).withSecond(0)
            val delayMillis = ChronoUnit.MILLIS.between(now, tomorrow)

            delay(delayMillis)

            while (true) {
                try {
                    sendDailySummary()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in daily summary: ${e.message}")
                }
                delay(TimeUnit.DAYS.toMillis(1)) // Wait 24 hours
            }
        }
    }

    fun recordAppSession(appName: String, packageName: String, startTime: Long, endTime: Long) {
        synchronized(dataLock) {
            try {
                val sessionStartTime = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(startTime), ZoneId.systemDefault())
                val sessionEndTime = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(endTime), ZoneId.systemDefault())

                val session = AnalyticsData.AppSession(
                    appName = appName,
                    packageName = packageName,
                    sessionStart = sessionStartTime,
                    sessionEnd = sessionEndTime
                )
                appSessions.getOrPut(packageName) { mutableListOf() }.add(session)
                Log.d(TAG, "Recorded app session for $appName (will be sent in next hourly batch)")
            } catch (e: Exception) {
                Log.e(TAG, "Error recording app session: ${e.message}")
            }
        }
    }

    fun recordAppTap(appName: String, packageName: String) {
        synchronized(dataLock) {
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
    }

    fun recordIntervention(
        appName: String,
        interventionType: String,
        videoDuration: Int? = null,
        requiredWatchTime: Int? = null,
        buttonClicked: String,
        interventionStartTime: Long? = null
    ) {
        synchronized(dataLock) {
            try {
                val currentTime = ZonedDateTime.now()
                val interventionStart = interventionStartTime?.let {
                    ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), ZoneId.systemDefault())
                } ?: currentTime

                val intervention = AnalyticsData.Intervention(
                    interventionStart = interventionStart,
                    interventionEnd = currentTime,
                    appName = appName,
                    interventionType = interventionType,
                    videoDuration = videoDuration,
                    requiredWatchTime = requiredWatchTime,
                    buttonClicked = buttonClicked
                )
                interventions.getOrPut(appName) { mutableListOf() }.add(intervention)
                Log.d(TAG, "Recorded intervention for $appName ($interventionType)")
            } catch (e: Exception) {
                Log.e(TAG, "Error recording intervention: ${e.message}")
            }
        }
    }

    private fun sendBatchData() {
        synchronized(dataLock) {
            if (isNetworkAvailable() && isBatteryLevelGood()) {
                Log.d(TAG, "Sending batch data to server...")

                // Collect all data into a single list
                val allData = mutableListOf<AnalyticsData>().apply {
                    addAll(appSessions.values.flatten())
                    addAll(appTaps.values.flatten())
                    addAll(interventions.values.flatten())
                }

                if (allData.isNotEmpty()) {
                    // Use coroutine to send data asynchronously
                    serviceScope.launch {
                        try {
                            val success = dataSyncService.sendBatchData(allData)
                            if (success) {
                                // Clear sent data on success
                                synchronized(dataLock) {
                                    appSessions.clear()
                                    appTaps.clear()
                                    interventions.clear()
                                }
                                Log.d(TAG, "Batch data sent and cleared successfully")
                            } else {
                                Log.w(TAG, "Failed to send batch data, will retry later")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending batch data: ${e.message}")
                        }
                    }
                } else {
                    Log.d(TAG, "No data to send")
                }
            } else {
                Log.d(TAG, "Skipping batch send - network or battery conditions not met")
            }
        }
    }

    private fun sendDailySummary() {
        // Implementation for daily summary
        Log.d(TAG, "Daily summary would be sent here")
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }

    private fun isBatteryLevelGood(): Boolean {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        return if (level != -1 && scale != -1) {
            val batteryPercent = (level.toFloat() / scale.toFloat()) * 100
            batteryPercent > 20 // Only send when battery > 20%
        } else {
            true // If can't determine battery, assume it's okay
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        INSTANCE = null
        hourlyJob?.cancel()
        dailyJob?.cancel()
        serviceScope.cancel()
    }
}
