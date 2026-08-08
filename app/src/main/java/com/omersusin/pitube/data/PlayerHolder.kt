package com.omersusin.pitube.data

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceMediaSource

object PlayerHolder {
    private var _player: ExoPlayer? = null
    
    fun getPlayer(context: Context): ExoPlayer {
        if (_player == null) {
            val isEnabled = PrefsManager.isVolumeNormalizationEnabled(context)
            
            val renderersFactory = object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean,
                    enableOffload: Boolean
                ): androidx.media3.exoplayer.audio.AudioSink {
                    return DefaultAudioSink.Builder(context)
                        .setAudioProcessorChain(
                            DefaultAudioSink.DefaultAudioProcessorChain(
                                VolumeNormalizationProcessor(isEnabled)
                            )
                        )
                        .build()
                }
            }
            
            _player = ExoPlayer.Builder(context, renderersFactory)
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
