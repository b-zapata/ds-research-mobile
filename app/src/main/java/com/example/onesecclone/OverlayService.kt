package com.example.onesecclone

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.content.Context
import android.os.CountDownTimer
import android.widget.Button
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.onesecclone.analytics.AnalyticsService

class OverlayService : Service() {
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var player: ExoPlayer? = null
    private var countdownTimer: CountDownTimer? = null
    private var interventionStartTime: Long = 0
    private var videoStartTime: Long = 0
    private var videoDuration: Long = 0
    private var wasVideoWatched: Boolean = false

    override fun onCreate() {
        super.onCreate()
        interventionStartTime = System.currentTimeMillis()
        startForeground()
        showOverlay()
    }

    private fun startForeground() {
        val channelId = createNotificationChannel()
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Video Overlay")
            .setContentText("Overlay is active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "OverlayServiceChannel"
            val channelName = "Overlay Service Channel"
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            return channelId
        }
        return ""
    }

    private fun showOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.OPAQUE
        )

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)
        overlayView?.setBackgroundColor(Color.BLACK)

        try {
            windowManager?.addView(overlayView, params)
            Log.d("OverlayService", "Overlay view added successfully")
        } catch (e: Exception) {
            Log.e("OverlayService", "Failed to add overlay view", e)
            stopSelf()
            return
        }

        setupButtons()
        initializePlayer()
    }

    private fun setupButtons() {
        val closeButton = overlayView?.findViewById<Button>(R.id.closeButton)
        val skipButton = overlayView?.findViewById<Button>(R.id.skipButton)

        skipButton?.isEnabled = false

        closeButton?.setOnClickListener {
            recordIntervention("Close app")

            player?.stop()
            player?.release()
            player = null

            countdownTimer?.cancel()
            countdownTimer = null

            val homeIntent = Intent(Intent.ACTION_MAIN)
            homeIntent.addCategory(Intent.CATEGORY_HOME)
            homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(homeIntent)

            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses("com.instagram.android")

            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                Log.e("OverlayService", "Error removing overlay view", e)
            }

            stopSelf()
        }

        skipButton?.setOnClickListener {
            recordIntervention("Skip to app")

            updateLastSkipTime()
            player?.release()
            stopSelf()
        }

        countdownTimer = object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = millisUntilFinished / 1000
                skipButton?.text = "Skip ($secondsLeft)"
                skipButton?.isEnabled = false
            }

            override fun onFinish() {
                skipButton?.text = "Skip video"
                skipButton?.isEnabled = true
                wasVideoWatched = true
            }
        }.start()
    }

    private fun initializePlayer() {
        try {
            val playerView = overlayView?.findViewById<PlayerView>(R.id.playerView)
            player = ExoPlayer.Builder(this).build()
            playerView?.player = player
            playerView?.useController = false

            val mediaItem = MediaItem.fromUri(Uri.parse("https://ds-research-videos-ben.s3.us-west-2.amazonaws.com/When%20Life%20Breaks%20You.mp4"))
            player?.setMediaItem(mediaItem)
            player?.prepare()
            player?.playWhenReady = true
            player?.volume = 1f

            videoStartTime = System.currentTimeMillis()

            player?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_IDLE ->
                            Log.d("OverlayService", "Player state: IDLE")
                        Player.STATE_BUFFERING ->
                            Log.d("OverlayService", "Player state: BUFFERING")
                        Player.STATE_READY -> {
                            Log.d("OverlayService", "Player state: READY")
                            videoDuration = player?.duration ?: 0
                        }
                        Player.STATE_ENDED -> {
                            Log.d("OverlayService", "Player state: ENDED")
                            recordIntervention("Video completed")
                            stopSelf()
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e("OverlayService", "Player error: ${error.message}")
                    recordIntervention("Video error")
                    stopSelf()
                }
            })

            Log.d("OverlayService", "Player initialized successfully")
        } catch (e: Exception) {
            Log.e("OverlayService", "Failed to initialize player", e)
            stopSelf()
        }
    }

    private fun recordIntervention(buttonClicked: String) {
        try {
            val interventionEndTime = System.currentTimeMillis()
            val watchTime = if (wasVideoWatched) 10000 else (System.currentTimeMillis() - videoStartTime).coerceAtLeast(0)

            AnalyticsService.recordInterventionStatic(
                appName = "Instagram",
                interventionType = "video_delay",
                videoDuration = (videoDuration / 1000).toInt(),
                requiredWatchTime = 10,
                buttonClicked = buttonClicked,
                interventionStartTime = interventionStartTime
            )

            Log.d("OverlayService", "Recorded intervention: $buttonClicked, watchTime: ${watchTime}ms, duration: ${interventionEndTime - interventionStartTime}ms")
        } catch (e: Exception) {
            Log.e("OverlayService", "Error recording intervention", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("OverlayService", "Service started")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        countdownTimer?.cancel()
        Log.d("OverlayService", "Service being destroyed")
        try {
            player?.release()
            windowManager?.removeView(overlayView)
        } catch (e: Exception) {
            Log.e("OverlayService", "Error during service destruction", e)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1002
        private var lastSkipTime = 0L
        const val COOLDOWN_PERIOD = 15000L

        fun getLastSkipTime(): Long = lastSkipTime
        fun updateLastSkipTime() {
            lastSkipTime = System.currentTimeMillis()
        }
    }
}
