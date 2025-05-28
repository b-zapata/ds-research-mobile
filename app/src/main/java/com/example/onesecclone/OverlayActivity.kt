package com.example.onesecclone

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class OverlayActivity : Activity() {

    private lateinit var player: ExoPlayer
    private var playbackPosition = 0L
    private var playWhenReady = true
    private var isReturningFromRecents = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return

        setContentView(R.layout.activity_overlay)

        val playerView = findViewById<PlayerView>(R.id.playerView)
        initializePlayer(playerView)
    }

    private fun initializePlayer(playerView: PlayerView) {
        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        val mediaItem = MediaItem.fromUri(Uri.parse("https://ds-research-videos-ben.s3.us-west-2.amazonaws.com/When%20Life%20Breaks%20You.mp4"))
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = playWhenReady
        player.seekTo(playbackPosition)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    finish()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (isReturningFromRecents) {
            playbackPosition = 0L
            player.seekTo(playbackPosition)
            isReturningFromRecents = false
        }
        player.play()
    }

    override fun onPause() {
        super.onPause()
        updatePlaybackPosition()
        player.pause()
    }

    override fun onStop() {
        super.onStop()
        isReturningFromRecents = true
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }

    private fun updatePlaybackPosition() {
        playbackPosition = player.currentPosition
        playWhenReady = player.playWhenReady
    }
}