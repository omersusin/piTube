package com.omersusin.pitube.data

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

class VolumeNormalizationProcessor(private val enabled: Boolean) : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!enabled || inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val sampleCount = inputBuffer.remaining() / 2
        if (sampleCount == 0) return
        val start = inputBuffer.position()

        var sum = 0.0
        for (i in 0 until sampleCount) {
            val s = inputBuffer.getShort(start + i * 2).toDouble()
            sum += s * s
        }
        val rms = Math.sqrt(sum / sampleCount)
        val target = 4000.0
        val gain = if (rms >= 1.0 && rms < target) (target / rms).coerceAtMost(2.5) else 1.0

        val output = replaceOutputBuffer(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val s = inputBuffer.getShort(start + i * 2)
            val boosted = (s * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output.putShort(boosted.toShort())
        }
        output.flip()
    }
}
