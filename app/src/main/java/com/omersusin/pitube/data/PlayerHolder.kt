package com.omersusin.pitube.data

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

object PlayerHolder {
    private var _player: ExoPlayer? = null
    private var lastEnabled: Boolean? = null

    fun getPlayer(context: Context): ExoPlayer {
        val enabled = PrefsManager.isVolumeNormalizationEnabled(context)
        val existing = _player
        if (existing == null || (lastEnabled != enabled && !existing.isPlaying)) {
            existing?.release()
            lastEnabled = enabled
            val factory = object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean,
                    enableOffload: Boolean
                ): AudioSink {
                    return DefaultAudioSink.Builder()
                        .setAudioProcessorChain(
                            DefaultAudioSink.DefaultAudioProcessorChain(
                                VolumeNormalizationProcessor(enabled)
                            )
                        )
                        .build()
                }
            }
            _player = ExoPlayer.Builder(context, factory)
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .build()
        }
        return _player!!
    }

    fun releasePlayer() { _player?.release(); _player = null }
}
