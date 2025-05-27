package com.example.onesecclone

import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.TimeUnit

data class AppUsageInfo(
    val packageName: String,
    val usageMinutes: Long
)

class ScreenTimeViewModel(application: Application) : AndroidViewModel(application) {

    private val _usageData = MutableLiveData<List<AppUsageInfo>>()
    val usageData: LiveData<List<AppUsageInfo>> = _usageData

    fun loadUsageStats(startTime: Long, endTime: Long) {
        val usageStatsManager = getApplication<Application>().getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        val allowedApps = setOf(
            "com.instagram.android",
            "com.facebook.katana",
            "com.google.android.youtube"
        )

        val aggregated = usageStats
            .filter { it.totalTimeInForeground > 0 }
            .groupBy { it.packageName }
            .filterKeys { it in allowedApps }
            .map { (packageName, statsList) ->
                val totalUsageMs = statsList.sumOf { it.totalTimeInForeground }
                val usageMinutes = totalUsageMs / (1000 * 60)
                AppUsageInfo(
                    packageName = packageName,
                    usageMinutes = usageMinutes
                )
            }

        _usageData.postValue(aggregated.sortedByDescending { it.usageMinutes })
    }
}
