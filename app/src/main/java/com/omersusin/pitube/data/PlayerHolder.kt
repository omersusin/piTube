package com.omersusin.pitube.data

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.exoplayer.ExoPlayer

object PlayerHolder {
    private var _player: ExoPlayer? = null

    fun getPlayer(context: Context): ExoPlayer {
        if (_player == null) {
            _player = ExoPlayer.Builder(context)
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .build()
        }
        return _player!!
    }

    fun releasePlayer() {
        _player?.release()
        _player = null
    }
}
