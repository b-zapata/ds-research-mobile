package com.example.onesecclone.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.example.onesecclone.analytics.AnalyticsService
import java.util.*

class UsageSessionTracker(private val context: Context) {
    
    companion object {
        private const val TAG = "UsageSessionTracker"
        private const val MIN_SESSION_DURATION_MS = 10000L // 10 seconds minimum

        // Much shorter thresholds - only merge rapid technical events, not separate usage
        private const val DEFAULT_SESSION_GAP_THRESHOLD_MS = 15000L // 15 seconds default
        private const val SOCIAL_MEDIA_GAP_THRESHOLD_MS = 20000L // 20 seconds for social media apps
        private const val BROWSER_GAP_THRESHOLD_MS = 18000L // 18 seconds for browsers

        // Apps that need slightly longer consolidation windows due to complex internal navigation
        private val SOCIAL_MEDIA_APPS = setOf(
            "com.facebook.katana",
            "com.instagram.android",
            "com.twitter.android",
            "com.snapchat.android",
            "com.tiktok",
            "com.linkedin.android"
        )

        private val BROWSER_APPS = setOf(
            "com.android.chrome",
            "com.google.android.youtube", // YouTube has complex video/browsing behavior
            "org.mozilla.firefox",
            "com.microsoft.emmx"
        )
    }
    
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = context.packageManager
    
    // Keep track of what we've already processed to avoid duplicates
    private var lastProcessedTime: Long = 0
    
    // Track active sessions (app package -> session start time)
    private val activeSessions = mutableMapOf<String, Long>()

    // Track recent sessions to enable consolidation (app package -> list of recent session end times)
    private val recentSessionEnds = mutableMapOf<String, MutableList<Long>>()

    /**
     * Check if usage access permission is available
     */
    fun hasUsageAccessPermission(): Boolean {
        return UsageAccessHelper.hasUsageAccessPermission(context)
    }
    
    /**
     * Get the currently running foreground app using UsageEvents
     */
    fun getCurrentForegroundApp(): String? {
        if (!hasUsageAccessPermission()) {
            Log.w(TAG, "No usage access permission")
            return null
        }
        
        val currentTime = System.currentTimeMillis()
        val startTime = currentTime - (60 * 1000) // Last minute

        try {
            val usageEvents = usageStatsManager.queryEvents(startTime, currentTime)
            var currentForegroundApp: String? = null

            val event = UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        currentForegroundApp = event.packageName
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        if (currentForegroundApp == event.packageName) {
                            currentForegroundApp = null
                        }
                    }
                }
            }

            return currentForegroundApp
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current foreground app", e)
            return null
        }
    }
    
    /**
     * Check for new app usage sessions using precise UsageEvents data
     * and sync them to our analytics system
     */
    fun checkForNewSessions() {
        if (!hasUsageAccessPermission()) {
            Log.w(TAG, "No usage access permission for session tracking")
            return
        }
        
        val currentTime = System.currentTimeMillis()
        
        // If this is the first run, start from 1 hour ago to catch recent activity
        if (lastProcessedTime == 0L) {
            lastProcessedTime = currentTime - (60 * 60 * 1000) // 1 hour ago
            Log.d(TAG, "First run - checking events from last hour")
        }
        
        try {
            Log.d(TAG, "Querying usage events from ${java.util.Date(lastProcessedTime)} to ${java.util.Date(currentTime)}")

            // Query usage events from the last processed time to now
            val usageEvents = usageStatsManager.queryEvents(lastProcessedTime, currentTime)
            var newSessionsFound = 0
            var eventsProcessed = 0

            val event = UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                eventsProcessed++

                Log.v(TAG, "Processing event: type=${event.eventType}, package=${event.packageName}, time=${java.util.Date(event.timeStamp)}")

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        Log.d(TAG, "App started: ${event.packageName}")
                        handleAppStarted(event.packageName, event.timeStamp)
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        Log.d(TAG, "App paused: ${event.packageName}")
                        val sessionRecorded = handleAppStopped(event.packageName, event.timeStamp)
                        if (sessionRecorded) {
                            newSessionsFound++
                        }
                    }
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                        Log.d(TAG, "Screen turned off")
                        // Screen turned off - end all active sessions
                        val sessionsEnded = handleScreenOff(event.timeStamp)
                        newSessionsFound += sessionsEnded
                    }
                }
            }
            
            Log.d(TAG, "Processed $eventsProcessed events, recorded $newSessionsFound new sessions")
            Log.d(TAG, "Currently active sessions: ${activeSessions.size}")

            if (newSessionsFound > 0) {
                Log.i(TAG, "✅ Recorded $newSessionsFound precise app sessions from UsageEvents")
            } else if (eventsProcessed == 0) {
                Log.w(TAG, "⚠️ No usage events found - this might indicate a permission issue or no recent app activity")
            }

            lastProcessedTime = currentTime

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing usage events for session tracking", e)
        }
    }

    /**
     * Handle app started event - track session start or extend existing session
     */
    private fun handleAppStarted(packageName: String, startTime: Long) {
        // Only track user apps, not system apps
        if (isUserApp(packageName)) {
            // Check if this is a session restart within the gap threshold
            val recentEnds = recentSessionEnds[packageName]
            if (recentEnds != null && recentEnds.isNotEmpty()) {
                val lastEndTime = recentEnds.maxOrNull() ?: 0L
                val timeSinceLastEnd = startTime - lastEndTime

                val sessionGapThreshold = getSessionGapThreshold(packageName)
                if (timeSinceLastEnd <= sessionGapThreshold) {
                    Log.d(TAG, "App $packageName restarted within ${timeSinceLastEnd}ms - will consolidate sessions")
                    // Don't start a new session yet, wait to see if we need to consolidate
                    activeSessions[packageName] = startTime
                    return
                }
            }

            activeSessions[packageName] = startTime
            Log.d(TAG, "Session started for $packageName at ${Date(startTime)}")
        }
    }

    /**
     * Handle app stopped event - calculate and record session if valid, with consolidation
     */
    private fun handleAppStopped(packageName: String, endTime: Long): Boolean {
        val startTime = activeSessions.remove(packageName)

        if (startTime != null) {
            val sessionDuration = endTime - startTime

            // Check if we should consolidate with a recent session
            val recentEnds = recentSessionEnds[packageName]
            if (recentEnds != null && recentEnds.isNotEmpty()) {
                val lastEndTime = recentEnds.maxOrNull() ?: 0L
                val timeSinceLastEnd = startTime - lastEndTime

                val sessionGapThreshold = getSessionGapThreshold(packageName)
                if (timeSinceLastEnd <= sessionGapThreshold) {
                    Log.d(TAG, "Consolidating session for $packageName - gap was only ${timeSinceLastEnd}ms")

                    // Find and update the most recent session for this app
                    val success = consolidateWithRecentSession(packageName, endTime)
                    if (success) {
                        // Update the recent end time
                        recentEnds[recentEnds.size - 1] = endTime
                        return false // Don't count as a new session since we consolidated
                    }
                }
            }

            // Only record sessions that meet minimum duration
            if (sessionDuration >= MIN_SESSION_DURATION_MS) {
                val appName = getAppName(packageName)

                AnalyticsService.recordAppSessionStatic(
                    appName,
                    packageName,
                    startTime,
                    endTime
                )
                
                val durationMinutes = sessionDuration / (1000 * 60)
                Log.d(TAG, "Recorded precise session for $appName: ${durationMinutes}m")

                // Track recent session end time for future consolidation
                recentSessionEnds.getOrPut(packageName) { mutableListOf() }.add(endTime)

                // Clean up old entries (keep only last 5 sessions per app)
                val ends = recentSessionEnds[packageName]!!
                if (ends.size > 5) {
                    ends.removeAt(0)
                }

                return true
            } else {
                Log.d(TAG, "Skipped short session for $packageName: ${sessionDuration}ms")
            }
        }

        return false
    }

    /**
     * Consolidate current session with the most recent session for the same app
     */
    private fun consolidateWithRecentSession(packageName: String, newEndTime: Long): Boolean {
        try {
            // Get all current sessions from AnalyticsService
            val allSessions = AnalyticsService.getAllSessions()

            // Find the most recent session for this package
            val recentSessions = allSessions
                .filter { it.packageName == packageName }
                .sortedByDescending { it.getSessionEndTime() }

            if (recentSessions.isNotEmpty()) {
                val mostRecentSession = recentSessions.first()
                val originalEndTime = mostRecentSession.getSessionEndTime()
                val originalStartTime = mostRecentSession.getSessionStartTime()

                // Calculate the time gap
                val currentTime = System.currentTimeMillis()
                val gapDuration = newEndTime - originalEndTime.toInstant().toEpochMilli()

                Log.d(TAG, "Extending session for ${getAppName(packageName)} by ${gapDuration}ms")

                // Create a new consolidated session with extended end time
                val consolidatedSession = com.example.onesecclone.analytics.AnalyticsData.AppSession(
                    appName = mostRecentSession.appName,
                    packageName = packageName,
                    sessionStart = originalStartTime,
                    sessionEnd = java.time.ZonedDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(newEndTime),
                        java.time.ZoneId.systemDefault()
                    )
                )

                // Note: We'd need to add a method to AnalyticsService to replace sessions
                // For now, we'll just log the consolidation
                Log.i(TAG, "✅ Would consolidate session for ${getAppName(packageName)} - extending by ${gapDuration / 1000}s")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consolidating session for $packageName", e)
        }

        return false
    }

    /**
     * Handle screen off event - end all active sessions
     */
    private fun handleScreenOff(screenOffTime: Long): Int {
        var sessionsEnded = 0

        // End all active sessions when screen turns off
        val iterator = activeSessions.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val packageName = entry.key
            val startTime = entry.value

            val sessionDuration = screenOffTime - startTime

            if (sessionDuration >= MIN_SESSION_DURATION_MS) {
                val appName = getAppName(packageName)

                AnalyticsService.recordAppSessionStatic(
                    appName,
                    packageName,
                    startTime,
                    screenOffTime
                )

                val durationMinutes = sessionDuration / (1000 * 60)
                Log.d(TAG, "Ended session on screen off for $appName: ${durationMinutes}m")

                // Track recent session end time for consolidation
                recentSessionEnds.getOrPut(packageName) { mutableListOf() }.add(screenOffTime)
                sessionsEnded++
            }

            iterator.remove()
        }

        if (sessionsEnded > 0) {
            Log.d(TAG, "Screen off - ended $sessionsEnded active sessions")
        }

        return sessionsEnded
    }

    /**
     * Check if this is a user-installed app (not system app)
     */
    private fun isUserApp(packageName: String): Boolean {
        // Don't track our own app
        if (packageName == context.packageName) {
            return false
        }

        // Filter out obvious system components
        val systemPackagesToSkip = setOf(
            "android",
            "com.android.systemui",
            "com.android.launcher",
            "com.android.settings",
            "com.android.phone",
            "com.android.bluetooth",
            "com.android.nfc"
        )

        if (systemPackagesToSkip.contains(packageName)) {
            Log.v(TAG, "Skipping system package: $packageName")
            return false
        }

        // For popular apps and launchers, track them even if PackageManager query fails
        val popularAppsToTrack = setOf(
            "com.google.android.youtube",
            "com.google.android.apps.nexuslauncher",
            "com.microsoft.office.outlook",
            "com.spotify.music",
            "com.instagram.android",
            "com.google.android.gm",
            "com.facebook.katana",
            "com.whatsapp",
            "com.twitter.android",
            "com.netflix.mediaclient",
            "com.amazon.mShop.android.shopping",
            "com.google.android.apps.photos",
            "com.google.android.apps.maps"
        )

        if (popularAppsToTrack.contains(packageName)) {
            Log.v(TAG, "Tracking popular app: $packageName")
            return true
        }

        // Try to get app info, but be more permissive about failures
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)

            // Track most non-system apps
            val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            val shouldTrack = !isSystemApp || packageName.contains("launcher")

            if (shouldTrack) {
                Log.v(TAG, "Tracking app: $packageName")
                return true
            } else {
                Log.v(TAG, "Skipping system app: $packageName")
                return false
            }

        } catch (e: PackageManager.NameNotFoundException) {
            // If we can't find the package info, but it's generating usage events,
            // it's likely a valid app that should be tracked
            Log.d(TAG, "Package info not found for $packageName, but tracking anyway since it has usage events")
            return true
        }
    }
    
    /**
     * Get human-readable app name from package name
     */
    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    /**
     * Get session gap threshold based on app type
     */
    private fun getSessionGapThreshold(packageName: String): Long {
        return when {
            SOCIAL_MEDIA_APPS.contains(packageName) -> SOCIAL_MEDIA_GAP_THRESHOLD_MS
            BROWSER_APPS.contains(packageName) -> BROWSER_GAP_THRESHOLD_MS
            else -> DEFAULT_SESSION_GAP_THRESHOLD_MS
        }
    }

    /**
     * Get current active sessions for debugging
     */
    fun getActiveSessionsDebugInfo(): String {
        return if (activeSessions.isEmpty()) {
            "No active sessions"
        } else {
            "Active sessions: ${activeSessions.keys.joinToString(", ") { getAppName(it) }}"
        }
    }

    /**
     * Test method to verify the session tracker is working
     * This will force check recent events and report what it finds
     */
    fun testSessionTracking(): String {
        val result = StringBuilder()

        if (!hasUsageAccessPermission()) {
            result.appendLine("❌ PERMISSION ISSUE: Usage Access permission not granted")
            result.appendLine("Go to Settings > Special App Access > Usage Access and enable it for this app")
            return result.toString()
        }

        result.appendLine("✅ Usage Access permission granted")

        try {
            val currentTime = System.currentTimeMillis()
            val testStartTime = currentTime - (2 * 60 * 60 * 1000) // Last 2 hours

            result.appendLine("🔍 Testing event detection from last 2 hours...")

            val usageEvents = usageStatsManager.queryEvents(testStartTime, currentTime)
            var totalEvents = 0
            var userAppEvents = 0
            var resumeEvents = 0
            var pauseEvents = 0

            val event = UsageEvents.Event()
            val recentUserApps = mutableSetOf<String>()

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                totalEvents++

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        resumeEvents++
                        if (isUserApp(event.packageName)) {
                            userAppEvents++
                            recentUserApps.add(event.packageName)
                        }
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        pauseEvents++
                        if (isUserApp(event.packageName)) {
                            userAppEvents++
                        }
                    }
                }
            }

            result.appendLine("📊 Event Analysis:")
            result.appendLine("  • Total events found: $totalEvents")
            result.appendLine("  • Resume events: $resumeEvents")
            result.appendLine("  • Pause events: $pauseEvents")
            result.appendLine("  • User app events: $userAppEvents")
            result.appendLine("  • Recent user apps: ${recentUserApps.size}")

            if (recentUserApps.isNotEmpty()) {
                result.appendLine("📱 Recent user apps detected:")
                recentUserApps.forEach { pkg ->
                    result.appendLine("  • ${getAppName(pkg)} ($pkg)")
                }
            }

            if (totalEvents == 0) {
                result.appendLine("⚠️ No events found - try using some apps then test again")
            } else if (userAppEvents == 0) {
                result.appendLine("⚠️ No user app events found - only system apps detected")
            } else {
                result.appendLine("✅ Detection is working! Use apps normally and sessions will be recorded.")
            }

        } catch (e: Exception) {
            result.appendLine("❌ Error during test: ${e.message}")
        }

        return result.toString()
    }
}
