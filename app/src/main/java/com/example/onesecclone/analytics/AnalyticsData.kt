package com.example.onesecclone.analytics

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

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
        val sessionId: String = UUID.randomUUID().toString(), // Unique identifier for this session
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
            sessionId = UUID.randomUUID().toString(),
            appName = appName,
            packageName = packageName,
            sessionStart = formatTimestamp(sessionStart),
            sessionEnd = formatTimestamp(sessionEnd)
        )

        fun getSessionStartTime(): ZonedDateTime = parseTimestamp(sessionStart)
        fun getSessionEndTime(): ZonedDateTime = parseTimestamp(sessionEnd)
    }

    data class Intervention(
        val eventType: String = "intervention",
        val interventionId: String = UUID.randomUUID().toString(), // Unique identifier for this intervention
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
            interventionId = UUID.randomUUID().toString(),
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
        val statusId: String = UUID.randomUUID().toString(), // Unique identifier for this status reading
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
            statusId = UUID.randomUUID().toString(),
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
        val summaryId: String = UUID.randomUUID().toString(), // Unique identifier for this daily summary
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
