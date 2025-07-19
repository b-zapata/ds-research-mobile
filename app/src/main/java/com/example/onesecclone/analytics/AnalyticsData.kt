package com.example.onesecclone.analytics

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

sealed class AnalyticsData {
    companion object {
        private val ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        fun formatTimestamp(zonedDateTime: ZonedDateTime): String {
            return zonedDateTime.format(ISO_FORMATTER)
        }

        fun parseTimestamp(timestampString: String): ZonedDateTime {
            return ZonedDateTime.parse(timestampString, ISO_FORMATTER)
        }
    }

    data class AppSession(
        val eventType: String = "app_session",
        val appName: String,
        val packageName: String,
        val sessionStart: String,
        val sessionEnd: String
    ) : AnalyticsData() {
        constructor(
            appName: String,
            packageName: String,
            sessionStart: ZonedDateTime,
            sessionEnd: ZonedDateTime
        ) : this(
            appName = appName,
            packageName = packageName,
            sessionStart = formatTimestamp(sessionStart),
            sessionEnd = formatTimestamp(sessionEnd)
        )

        fun getSessionStartTime(): ZonedDateTime = parseTimestamp(sessionStart)
        fun getSessionEndTime(): ZonedDateTime = parseTimestamp(sessionEnd)
    }

    data class AppTap(
        val eventType: String = "app_tap",
        val timestamp: String,
        val appName: String,
        val packageName: String
    ) : AnalyticsData() {
        constructor(
            timestamp: ZonedDateTime,
            appName: String,
            packageName: String
        ) : this(
            timestamp = formatTimestamp(timestamp),
            appName = appName,
            packageName = packageName
        )

        fun getTimestamp(): ZonedDateTime = parseTimestamp(timestamp)
    }

    data class Intervention(
        val eventType: String = "intervention",
        val interventionStart: String,
        val interventionEnd: String,
        val appName: String,
        val interventionType: String,
        val videoDuration: Int? = null,
        val requiredWatchTime: Int? = null,
        val buttonClicked: String
    ) : AnalyticsData() {
        constructor(
            interventionStart: ZonedDateTime,
            interventionEnd: ZonedDateTime,
            appName: String,
            interventionType: String,
            videoDuration: Int? = null,
            requiredWatchTime: Int? = null,
            buttonClicked: String
        ) : this(
            interventionStart = formatTimestamp(interventionStart),
            interventionEnd = formatTimestamp(interventionEnd),
            appName = appName,
            interventionType = interventionType,
            videoDuration = videoDuration,
            requiredWatchTime = requiredWatchTime,
            buttonClicked = buttonClicked
        )

        fun getInterventionStartTime(): ZonedDateTime = parseTimestamp(interventionStart)
        fun getInterventionEndTime(): ZonedDateTime = parseTimestamp(interventionEnd)
    }

    data class DeviceStatus(
        val eventType: String = "device_status",
        val batteryLevel: Int,
        val isCharging: Boolean,
        val connectionType: String,
        val connectionStrength: String,
        val appVersion: String,
        val lastBatchSent: String
    ) : AnalyticsData() {
        constructor(
            batteryLevel: Int,
            isCharging: Boolean,
            connectionType: String,
            connectionStrength: String,
            appVersion: String,
            lastBatchSent: ZonedDateTime
        ) : this(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            connectionType = connectionType,
            connectionStrength = connectionStrength,
            appVersion = appVersion,
            lastBatchSent = formatTimestamp(lastBatchSent)
        )

        fun getLastBatchSentTime(): ZonedDateTime = parseTimestamp(lastBatchSent)
    }

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
