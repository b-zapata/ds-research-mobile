package com.example.onesecclone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Log

class MainService : Service() {

    private val handler = Handler()
    private var lastPackage: String? = null
    private var overlayShownTime: Long = 0L
    private val targetApps = listOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically" // TikTok
    )

    private val checkRunnable = object : Runnable {
        override fun run() {
            val topApp = getForegroundApp()
            Log.d("OneSecClone", "Foreground app: $topApp")

            val currentTime = System.currentTimeMillis()
            if (topApp != null && targetApps.contains(topApp)) {
                if (currentTime - overlayShownTime >= 10000) { // 10 seconds
                    overlayShownTime = currentTime
                    Log.d("OneSecClone", "Launching overlay for $topApp")

                    val intent = Intent(this@MainService, OverlayActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    val pendingIntent = PendingIntent.getActivity(
                        this@MainService,
                        0,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    pendingIntent.send()
                }
            }

            handler.postDelayed(this, 1000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, "onesec_service_channel")
            .setContentTitle("OneSec Clone is running")
            .setContentText("Monitoring app usage...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build()

        startForeground(
            1,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        handler.post(checkRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun getForegroundApp(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 10,
            time
        )
        val recentUsage = usageStats.maxByOrNull { it.lastTimeUsed }
        return recentUsage?.packageName
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "onesec_service_channel",
                "OneSec Background Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
