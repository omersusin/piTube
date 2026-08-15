package com.omersusin.pitube.recognition

import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Captures microphone audio as 16 kHz / mono / 16-bit PCM (the format the
 * Shazam fingerprint generator and Whisper both accept), wraps it into a WAV,
 * and emits live RMS levels for the waveform UI.
 */
class MicAudioCapturer(
    private val sampleRateHz: Int = 16000,
    private val source: Int = MediaRecorder.AudioSource.MIC,
) {
    private var audioRecord: AudioRecord? = null

    /** Minimum buffer size guaranteed readable without overflow. */
    private val frameBytes = 1024

    /**
     * Record until [durationMs] elapses or [interrupted] flips false, whichever
     * comes first. `interrupted` is polled between chunks so a second mic tap
     * can stop an early. Returns captured PCM + WAV + levels.
     */
    fun record(
        durationMs: Long,
        interrupted: () -> Boolean = { false },
        onLevel: (Float) -> Unit = {},
    ): CapturedAudio {
        val minBuffer = maxOf(
            AudioRecord.getMinBufferSize(
                sampleRateHz,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
            ),
            frameBytes * 4,
        )
        val record =
            AudioRecord(
                source,
                sampleRateHz, 
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                minBuffer,
            )
        audioRecord = record
        val pcmOut = ByteArrayOutputStream()
        val chunk = ByteArray(frameBytes)
        val levels = mutableListOf<Float>()
        val maxSamples = (durationMs * sampleRateHz / 1000).toInt()
        var totalSamples = 0

        record.startRecording()
        try {
            while (totalSamples < maxSamples && !interrupted()) {
                val read = record.read(chunk, 0, chunk.size)
                if (read <= 0) break
                pcmOut.write(chunk, 0, read)

                val samplesRead = read / 2
                var sumSquares = 0.0
                val shortView = ByteBuffer.wrap(chunk, 0, read).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                while (shortView.hasRemaining()) {
                    val s = shortView.get().toDouble() / Short.MAX_VALUE
                    sumSquares += s * s
                }
                val rms = kotlin.math.sqrt(sumSquares / samplesRead.coerceAtLeast(1)).toFloat()
                levels.add(rms.coerceIn(0f, 1f))
                onLevel(rms.coerceIn(0f, 1f))
                totalSamples += samplesRead
            }
        } finally {
            runCatching { record.stop() }
            runCatching { record.release() }
            audioRecord = null
        }

        val pcmBytes = pcmOut.toByteArray()
        val sampleCount = pcmBytes.size / 2
        val pcmShorts = ShortArray(sampleCount)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcmShorts)

        return CapturedAudio(
            pcm = pcmShorts,
            wavBytes = encodeWav(pcmBytes, sampleRateHz),
            durationMs = (sampleCount * 1000L) / sampleRateHz,
            levels = levels,
        )
    }

    fun stop() {
        audioRecord?.let {
            runCatching { it.stop() }
        }
    }

    private fun encodeWav(
        pcm: ByteArray,
        sampleRate: Int,
    ): ByteArray {
        val dataSize = pcm.size
        val out = ByteArrayOutputStream(dataSize + 44)
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.writeLittleInt(36 + dataSize)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        out.writeLittleInt(16) // fmt chunk size
        out.writeLittleShort(1) // PCM
        out.writeLittleShort(1) // mono
        out.writeLittleInt(sampleRate)
        out.writeLittleInt(sampleRate * 2) // byte rate
        out.writeLittleShort(2) // block align
        out.writeLittleShort(16) // bits per sample
        out.write("data".toByteArray(Charsets.US_ASCII))
        out.writeLittleInt(dataSize)
        out.write(pcm)
        return out.toByteArray()
    }
}

private fun ByteArrayOutputStream.writeLittleInt(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
    write((value ushr 16) and 0xFF)
    write((value ushr 24) and 0xFF)
}

private fun ByteArrayOutputStream.writeLittleShort(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
}

/**
 * Extracts the raw 16 kHz PCM payload from a WAV produced by [MicAudioCapturer]
 * (chunk-walked so it also tolerates extra chunks).
 */
fun wavToPcm16(wavBytes: ByteArray): ShortArray? {
    if (wavBytes.size < 44) return null
    if (wavBytes[0] != 'R'.code.toByte() || wavBytes[1] != 'I'.code.toByte() ||
        wavBytes[2] != 'F'.code.toByte() || wavBytes[3] != 'F'.code.toByte()
    ) {
        return null
    }
    val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
    var offset = 12
    while (offset + 8 <= wavBytes.size) {
        val chunkId = String(wavBytes, offset, 4, Charsets.US_ASCII)
        val chunkSize = buffer.getInt(offset + 4)
        if (chunkId == "data") {
            val dataStart = offset + 8
            val dataSize = chunkSize.coerceAtMost(wavBytes.size - dataStart)
            val sampleCount = dataSize / 2
            val out = ShortArray(sampleCount)
            for (i in 0 until sampleCount) {
                out[i] = buffer.getShort(dataStart + i * 2)
            }
            return out
        }
        offset += 8 + chunkSize + (chunkSize and 1)
    }
    return null
}

/**
 * Thrown when the caller never obtained RECORD_AUDIO permission.
 */
class MicPermissionException : IOException("Record audio permission is required")