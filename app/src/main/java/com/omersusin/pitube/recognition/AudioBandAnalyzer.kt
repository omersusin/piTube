package com.omersusin.pitube.recognition

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

internal class Fft(
    private val n: Int,
) {
    private val cosTable = DoubleArray(n / 2) { i -> cos(2.0 * PI * i / n) }
    private val sinTable = DoubleArray(n / 2) { i -> sin(2.0 * PI * i / n) }
    private val bitReversal =
        IntArray(n).also { out ->
            val bits = Integer.numberOfTrailingZeros(n)
            for (i in 0 until n) {
                out[i] = i.reverseBits(bits)
            }
        }

    fun fft(
        real: DoubleArray,
        imag: DoubleArray,
    ) {
        for (i in 0 until n) {
            val j = bitReversal[i]
            if (j > i) {
                val tr = real[i]
                real[i] = real[j]
                real[j] = tr
                val ti = imag[i]
                imag[i] = imag[j]
                imag[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val tableStep = n / len
            var i = 0
            while (i < n) {
                var j = 0
                var k = 0
                while (j < halfLen) {
                    val cos = cosTable[k]
                    val sin = sinTable[k]
                    val i1 = i + j
                    val i2 = i1 + halfLen

                    val r2 = real[i2]
                    val im2 = imag[i2]
                    val tpre = r2 * cos + im2 * sin
                    val tpim = -r2 * sin + im2 * cos

                    real[i2] = real[i1] - tpre
                    imag[i2] = imag[i1] - tpim
                    real[i1] += tpre
                    imag[i1] += tpim

                    j++
                    k += tableStep
                }
                i += len
            }
            len = len shl 1
        }
    }
}

private fun Int.reverseBits(bitCount: Int): Int {
    var x = this
    var y = 0
    repeat(bitCount) {
        y = (y shl 1) or (x and 1)
        x = x ushr 1
    }
    return y
}

/**
 * Splits live mic chunks into [bandCount] normalized frequency-band energies
 * (log-spaced, ~40 Hz – 8 kHz) for audio-reactive visuals. All buffers are
 * preallocated and reused — [analyze] allocates nothing per chunk.
 */
internal class AudioBandAnalyzer(
    private val sampleRateHz: Int = 16000,
    private val fftSize: Int = 512,
    private val bandCount: Int = 8,
) {
    private val fft = Fft(fftSize)
    private val real = DoubleArray(fftSize)
    private val imag = DoubleArray(fftSize)
    private val hann = DoubleArray(fftSize) { i ->
        0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))
    }
    private val bandEnergy = DoubleArray(bandCount)
    private val runningPeak = DoubleArray(bandCount) { 1e-6 }
    private val out = FloatArray(bandCount)

    /** Inclusive-exclusive FFT-bin edges of the log-spaced bands. */
    private val binEdges = IntArray(bandCount + 1).also { edges ->
        val minHz = 40.0
        val maxHz = minOf(8000.0, sampleRateHz / 2.0)
        val binWidth = sampleRateHz.toDouble() / fftSize
        for (i in 0..bandCount) {
            val hz = minHz * (maxHz / minHz).pow(i.toDouble() / bandCount)
            edges[i] = (hz / binWidth).toInt().coerceIn(1, fftSize / 2)
        }
        // Guarantee strictly increasing edges so every band covers >= 1 bin.
        for (i in 1..bandCount) {
            if (edges[i] <= edges[i - 1]) edges[i] = edges[i - 1] + 1
        }
        edges[bandCount] = edges[bandCount].coerceAtMost(fftSize / 2)
    }

    /**
     * Analyzes one PCM16 little-endian chunk ([read] bytes of [chunk],
     * zero-padded to [fftSize] samples on partial reads) and returns the
     * shared [out] array of AGC-normalized band energies in 0..1.
     */
    fun analyze(chunk: ByteArray, read: Int): FloatArray {
        val shortView =
            ByteBuffer.wrap(chunk, 0, read).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        var i = 0
        while (i < fftSize) {
            val s = if (shortView.hasRemaining()) shortView.get().toDouble() / Short.MAX_VALUE else 0.0
            real[i] = s * hann[i]
            imag[i] = 0.0
            i++
        }

        fft.fft(real, imag)

        bandEnergy.fill(0.0)
        for (bin in 1 until fftSize / 2) {
            val magnitudeSq = real[bin] * real[bin] + imag[bin] * imag[bin]
            for (b in 0 until bandCount) {
                if (bin >= binEdges[b] && bin < binEdges[b + 1]) {
                    bandEnergy[b] += magnitudeSq
                }
            }
        }

        for (b in 0 until bandCount) {
            runningPeak[b] = maxOf(bandEnergy[b], runningPeak[b] * 0.995)
            val normalized = (bandEnergy[b] / runningPeak[b]).coerceIn(0.0, 1.0)
            out[b] = normalized.pow(0.6).toFloat()
        }
        return out
    }
}
