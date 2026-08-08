package com.omersusin.pitube.data

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.Util
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VolumeNormalizationProcessor(private val enabled: Boolean) : BaseAudioProcessor() {
    private var inputEnded = false
    private var outputBuffer: ByteBuffer? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!enabled || inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!enabled) {
            replaceOutputBuffer(inputBuffer.remaining()) { it.put(inputBuffer) }
            return
        }

        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val frameCount = (limit - position) / 2 // 16-bit PCM = 2 bytes

        if (frameCount <= 0) {
            super.queueInput(inputBuffer)
            return
        }

        // Calculate RMS
        var sumSquares = 0.0
        for (i in 0 until frameCount) {
            val sample = inputBuffer.getShort(position + i * 2)
            sumSquares += (sample * sample).toDouble()
        }
        val rms = Math.sqrt(sumSquares / frameCount)
        
        // Target RMS ~3000 (roughly -14 LUFS equivalent for PCM)
        // If quiet, boost. If loud, do nothing to avoid clipping.
        var gain = if (rms > 0 && rms < 3000.0) (3000.0 / rms).coerceAtMost(2.0).toFloat() else 1.0f

        val output = replaceOutputBuffer((limit - position))
        for (i in 0 until frameCount) {
            var sample = (inputBuffer.getShort(position + i * 2) * gain).toInt()
            // Hard limiter to prevent clipping
            if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE.toInt()
            if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE.toInt()
            output.putShort(sample.toShort())
        }
        inputBuffer.position(limit)
    }
    
    private fun replaceOutputBuffer(size: Int, action: (ByteBuffer) -> Unit): ByteBuffer {
        val buffer = super.replaceOutputBuffer(size)
        action(buffer)
        return buffer
    }
}
