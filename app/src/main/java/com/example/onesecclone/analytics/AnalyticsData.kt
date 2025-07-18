package com.example.onesecclone.analytics

import java.time.LocalDateTime
import java.time.ZonedDateTime

sealed class AnalyticsData {
    data class AppSession(
        val eventType: String = "app_session",
        val appName: String,
        val packageName: String,
        val sessionStart: ZonedDateTime,
        val sessionEnd: ZonedDateTime
    ) : AnalyticsData()

    data class AppTap(
        val eventType: String = "app_tap",
        val timestamp: ZonedDateTime,
        val appName: String,
        val packageName: String
    ) : AnalyticsData()

    data class Intervention(
        val eventType: String = "intervention",
        val interventionStart: ZonedDateTime,
        val interventionEnd: ZonedDateTime,
        val appName: String,
        val interventionType: String,
        val videoDuration: Int? = null,
        val requiredWatchTime: Int? = null,
        val buttonClicked: String
    ) : AnalyticsData()

    data class DeviceStatus(
        val eventType: String = "device_status",
        val batteryLevel: Int,
        val isCharging: Boolean,
        val connectionType: String,
        val connectionStrength: String,
        val appVersion: String,
        val lastBatchSent: ZonedDateTime
    ) : AnalyticsData()

    data class DailySummary(
        val eventType: String = "daily_summary",
        val date: String,
        val totalScreenTime: Int,
        val appTotals: Map<String, AppStats>
    ) : AnalyticsData()

    data class AppStats(
        val minutes: Int,
        val sessions: Int,
        val totalTaps: Int,
        val totalDelays: Int,
        val totalAbandonments: Int,
        val totalInterruptions: Int
    )
}
