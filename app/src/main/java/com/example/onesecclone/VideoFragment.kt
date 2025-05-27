package com.example.onesecclone

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class VideoPlayerFragment : Fragment() {
    private var player: ExoPlayer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_video, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        player = ExoPlayer.Builder(requireContext()).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse("https://ds-research-videos-ben.s3.us-west-2.amazonaws.com/A%2B+STUDENT+MENTALITY+-+Best+Study+Motivation+%5Bg6BtbIiJ_rc%5D.webm"))
            setMediaItem(mediaItem)
            prepare()
            play()
        }

        view.findViewById<PlayerView>(R.id.playerView).player = player
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        player = null
    }
}