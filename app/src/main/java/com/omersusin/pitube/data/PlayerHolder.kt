package com.omersusin.pitube.data

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.atomic.AtomicReference

object PlayerHolder {
    private val _player = AtomicReference<ExoPlayer?>(null)

    fun getPlayer(context: Context): ExoPlayer {
        return _player.get() ?: run {
            val player = ExoPlayer.Builder(context)
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .build()
            if (_player.compareAndSet(null, player)) player else {
                player.release()
                _player.get()!!
            }
        }
    }

    fun applyPrefs(context: Context) {
        val p = _player.get() ?: return
        p.volume = if (PrefsManager.isVolumeNormalizationEnabled(context)) 0.7f else 1.0f
    }

    fun releasePlayer() {
        _player.getAndSet(null)?.release()
    }
}
