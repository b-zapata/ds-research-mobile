package com.example.onesecclone.usage

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.example.onesecclone.analytics.AnalyticsService
import java.util.*

class UsageSessionTracker(private val context: Context) {
    
    companion object {
        private const val TAG = "UsageSessionTracker"
    }
    
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = context.packageManager
    
    // Keep track of what we've already processed to avoid duplicates
    private var lastProcessedTime: Long = 0
    
    /**
     * Check if usage access permission is available
     */
    fun hasUsageAccessPermission(): Boolean {
        return UsageAccessHelper.hasUsageAccessPermission(context)
    }
    
    /**
     * Get the currently running foreground app using UsageStats
     */
    fun getCurrentForegroundApp(): String? {
        if (!hasUsageAccessPermission()) {
            Log.w(TAG, "No usage access permission")
            return null
        }
        
        val time = System.currentTimeMillis()
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 60, // Last minute
            time
        )
        
        if (usageStats.isEmpty()) {
            return null
        }
        
        // Find the most recently used app
        val recentApp = usageStats.maxByOrNull { it.lastTimeUsed }
        return recentApp?.packageName
    }
    
    /**
     * Check for new app usage sessions from Android's built-in UsageStats
     * and sync them to our analytics system
     */
    fun checkForNewSessions() {
        if (!hasUsageAccessPermission()) {
            Log.w(TAG, "No usage access permission for session tracking")
            return
        }
        
        val currentTime = System.currentTimeMillis()
        
        // If this is the first run, start from 24 hours ago
        if (lastProcessedTime == 0L) {
            lastProcessedTime = currentTime - (24 * 60 * 60 * 1000)
        }
        
        try {
            // Query usage stats from the last processed time to now
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                lastProcessedTime,
                currentTime
            )
            
            var newSessionsFound = 0
            
            for (stat in usageStats) {
                // Only process apps that have been used since our last check
                if (stat.lastTimeUsed > lastProcessedTime && stat.totalTimeInForeground > 0) {
                    // Convert Android's usage data to our session format
                    syncUsageStatToAnalytics(stat)
                    newSessionsFound++
                }
            }
            
            if (newSessionsFound > 0) {
                Log.d(TAG, "Synced $newSessionsFound usage sessions from Android UsageStats")
            }
            
            lastProcessedTime = currentTime
            
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing usage sessions from Android UsageStats", e)
        }
    }
    
    /**
     * Convert Android's UsageStats data to our analytics format
     */
    private fun syncUsageStatToAnalytics(usageStat: UsageStats) {
        try {
            val appName = getAppName(usageStat.packageName)
            
            // Android's UsageStats gives us total time and last used time
            // We'll estimate session start as (lastTimeUsed - totalTimeInForeground)
            val estimatedStartTime = usageStat.lastTimeUsed - usageStat.totalTimeInForeground
            val endTime = usageStat.lastTimeUsed
            
            // Only record sessions that are meaningful (more than 10 seconds)
            if (usageStat.totalTimeInForeground > 10000) {
                AnalyticsService.recordAppSessionStatic(
                    appName, 
                    usageStat.packageName, 
                    estimatedStartTime, 
                    endTime
                )
                
                val durationMinutes = usageStat.totalTimeInForeground / (1000 * 60)
                Log.d(TAG, "Synced session for $appName: ${durationMinutes}m")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error converting usage stat for ${usageStat.packageName}", e)
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
}

