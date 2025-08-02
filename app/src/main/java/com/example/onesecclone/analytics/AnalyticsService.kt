package com.example.onesecclone.analytics

import android.app.Service
import android.content.Intent
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
                    val interventionCount = instance.interventions.values.sumOf { it.size }
                    "📊 Analytics Status: $sessionCount sessions, $interventionCount interventions"
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

        fun getAllInterventions(): List<AnalyticsData.Intervention> {
            val instance = getInstance()
            return if (instance != null) {
                synchronized(instance.dataLock) {
                    instance.interventions.values.flatten()
                }
            } else emptyList()
        }

        // Static method to clear all analytics data
        fun clearAllDataStatic(): Int {
            val instance = getInstance()
            return instance?.clearAllData() ?: 0
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var hourlyJob: Job? = null
    private var dailyJob: Job? = null

    // Thread-safe collections to prevent race conditions
    val dataLock = Any()
    private val appSessions = mutableMapOf<String, MutableList<AnalyticsData.AppSession>>()
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
                val sessionStartTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault())
                val sessionEndTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneId.systemDefault())

                val session = AnalyticsData.AppSession(
                    appName = appName,
                    packageName = packageName,
                    sessionStart = sessionStartTime,
                    sessionEnd = sessionEndTime
                )
                appSessions.getOrPut(packageName) { mutableListOf() }.add(session)
                Log.d(TAG, "Recorded app session for $appName")
            } catch (e: Exception) {
                Log.e(TAG, "Error recording app session: ${e.message}")
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
                val startTime = if (interventionStartTime != null) {
                    ZonedDateTime.ofInstant(Instant.ofEpochMilli(interventionStartTime), ZoneId.systemDefault())
                } else {
                    ZonedDateTime.now().minusSeconds(videoDuration?.div(1000)?.toLong() ?: 5)
                }

                val intervention = AnalyticsData.Intervention(
                    interventionStart = startTime,
                    interventionEnd = ZonedDateTime.now(),
                    appName = appName,
                    interventionType = interventionType,
                    videoDuration = videoDuration,
                    requiredWatchTime = requiredWatchTime,
                    buttonClicked = buttonClicked
                )

                val packageName = getPackageNameFromAppName(appName)
                interventions.getOrPut(packageName) { mutableListOf() }.add(intervention)
                Log.d(TAG, "Recorded intervention for $appName with button: $buttonClicked")
            } catch (e: Exception) {
                Log.e(TAG, "Error recording intervention: ${e.message}")
            }
        }
    }

    private fun getPackageNameFromAppName(appName: String): String {
        return when (appName.lowercase()) {
            "instagram" -> "com.instagram.android"
            "facebook" -> "com.facebook.katana"
            "youtube" -> "com.google.android.youtube"
            "tiktok" -> "com.zhiliaoapp.musically"
            "snapchat" -> "com.snapchat.android"
            "twitter", "x" -> "com.twitter.android"
            else -> "unknown.app.package"
        }
    }

    private suspend fun sendBatchData() {
        val allData = mutableListOf<AnalyticsData>()

        // Collect data inside synchronized block
        synchronized(dataLock) {
            // Collect all sessions
            appSessions.values.forEach { sessionList ->
                allData.addAll(sessionList)
            }

            // Collect all interventions
            interventions.values.forEach { interventionList ->
                allData.addAll(interventionList)
            }
        }

        // Send data outside synchronized block to avoid suspension point in critical section
        if (allData.isNotEmpty()) {
            Log.i(TAG, "Sending batch of ${allData.size} analytics items")

            try {
                val success = dataSyncService.sendBatchData(allData)
                if (success) {
                    // Clear sent data inside synchronized block
                    synchronized(dataLock) {
                        appSessions.clear()
                        interventions.clear()
                    }
                    Log.i(TAG, "Successfully sent and cleared ${allData.size} analytics items")
                } else {
                    Log.w(TAG, "Failed to send batch data, will retry next hour")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending batch data: ${e.message}")
            }
        }
    }

    private suspend fun sendDailySummary() {
        try {
            val yesterday = LocalDate.now().minusDays(1)
            Log.d(TAG, "Generating daily summary for $yesterday")

            // Generate summary based on collected data
            val summary = AnalyticsData.DailySummary(
                date = yesterday.toString(),
                totalScreenTime = 0, // Would need to calculate from sessions
                appTotals = mapOf() // Would need to aggregate from collected data
            )

            dataSyncService.sendData(summary)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending daily summary: ${e.message}")
        }
    }

    fun clearAllData(): Int {
        synchronized(dataLock) {
            val totalItems = appSessions.values.sumOf { it.size } + interventions.values.sumOf { it.size }
            appSessions.clear()
            interventions.clear()
            Log.i(TAG, "Cleared $totalItems analytics items")
            return totalItems
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
