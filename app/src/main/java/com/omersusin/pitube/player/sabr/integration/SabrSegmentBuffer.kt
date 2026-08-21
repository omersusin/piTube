package com.omersusin.pitube.player.sabr.integration

import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SabrSegmentBuffer {
    private val queue = LinkedBlockingQueue<ByteArray>()
    private var currentChunk: ByteArray? = null
    private var currentOffset = 0
    private val closed = AtomicBoolean(false)
    private val endOfStream = AtomicBoolean(false)

    @Volatile
    private var lastDataAtMs: Long = System.currentTimeMillis()

    companion object {
        private const val STALL_TIMEOUT_MS = 30_000L
    }

    fun appendSegment(data: ByteArray) {
        if (closed.get()) return
        if (data.isNotEmpty()) {
            lastDataAtMs = System.currentTimeMillis()
            queue.put(data)
        }
    }

    fun signalEndOfStream() {
        endOfStream.set(true)
        queue.put(ByteArray(0))
    }

    fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (closed.get()) return -1
        if (length == 0) return 0

        var totalRead = 0
        while (totalRead < length) {
            if (currentChunk == null || currentOffset >= currentChunk!!.size) {
                val next = if (totalRead > 0) {
                    queue.poll()
                } else {
                    var polled: ByteArray? = null
                    var waitedMs = 0L
                    while (polled == null && !closed.get()) {
                        if (endOfStream.get() && queue.isEmpty()) break
                        polled = try {
                            queue.poll(250, TimeUnit.MILLISECONDS)
                        } catch (error: InterruptedException) {
                            Thread.currentThread().interrupt()
                            throw IOException("Interrupted while waiting for SABR media", error)
                        }
                        if (polled == null) {
                            waitedMs += 250
                            val idleMs = System.currentTimeMillis() - lastDataAtMs
                            // Producer died without EOS (e.g. follow-up loop broke on error):
                            // fail loudly so ExoPlayer raises a real error instead of hanging in
                            // STATE_BUFFERING forever.
                            if (idleMs >= STALL_TIMEOUT_MS) {
                                throw IOException(
                                    "SABR media stall: no segments for ${idleMs}ms " +
                                        "(eos=${endOfStream.get()}, closed=${closed.get()})"
                                )
                            }
                        }
                    }
                    polled
                }

                if (next == null) {
                    if (closed.get() || (endOfStream.get() && queue.isEmpty())) {
                        return if (totalRead > 0) totalRead else -1
                    }
                    continue
                }

                if (next.isEmpty()) {
                    return if (totalRead > 0) totalRead else -1
                }

                currentChunk = next
                currentOffset = 0
            }

            val chunk = currentChunk!!
            val available = chunk.size - currentOffset
            val toRead = minOf(available, length - totalRead)
            System.arraycopy(chunk, currentOffset, buffer, offset + totalRead, toRead)
            currentOffset += toRead
            totalRead += toRead
        }
        return totalRead
    }

    fun close() {
        closed.set(true)
        queue.clear()
        currentChunk = null
        currentOffset = 0
    }

    fun reset() {
        queue.clear()
        currentChunk = null
        currentOffset = 0
        closed.set(false)
        endOfStream.set(false)
        lastDataAtMs = System.currentTimeMillis()
    }
}
