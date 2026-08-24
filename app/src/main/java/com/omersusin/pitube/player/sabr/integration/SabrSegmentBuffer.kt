package com.omersusin.pitube.player.sabr.integration

import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SabrSegmentBuffer {
    private val queue = LinkedBlockingQueue<ByteArray>()
    private val headQueue = java.util.ArrayDeque<ByteArray>() // init replays land here
    private var currentChunk: ByteArray? = null
    private var currentOffset = 0
    private val closed = AtomicBoolean(false)
    private val endOfStream = AtomicBoolean(false)

    @Volatile
    private var lastDataAtMs: Long = System.currentTimeMillis()

    /** Retained fMP4/WebM init bytes so a bare re-prepare can replay them. */
    @Volatile
    private var retainedInit: ByteArray? = null

    @Volatile
    private var initPendingInQueue = false

    /**
     * While the user has paused, media delivery legitimately stops; the stall
     * watchdog must not convert a deliberate pause into a fatal Source error.
     */
    @Volatile
    private var paused = false

    companion object {
        private const val STALL_TIMEOUT_MS = 30_000L
    }

    fun setPaused(value: Boolean) {
        paused = value
        if (!value) lastDataAtMs = System.currentTimeMillis() // fresh budget on resume
    }

    /**
     * Store the init segment for this track and queue it once for first play.
     * The retained copy is what [replayInitForReopen] re-serves after a bare
     * prepare() restarts extraction mid-stream.
     */
    fun retainInit(data: ByteArray) {
        if (closed.get()) return
        val copy = data.copyOf()
        retainedInit = copy
        if (!initPendingInQueue) {
            headQueue.addLast(copy)
            initPendingInQueue = true
        }
        lastDataAtMs = System.currentTimeMillis()
    }

    /**
     * Called from [SabrExoPlayerDataSource.open]. On a RE-open (error recovery,
     * refocus prepare) the extractor starts sniffing from byte 0 again, so the
     * retained init segment must be served ahead of any queued media — without
     * it the extractor fails with NoDeclaredBrand.
     */
    fun replayInitForReopen() {
        val data = retainedInit ?: return
        if (!initPendingInQueue) {
            headQueue.addFirst(data.copyOf())
            initPendingInQueue = true
            lastDataAtMs = System.currentTimeMillis()
        }
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
                    if (headQueue.isNotEmpty()) {
                        headQueue.pollFirst().also { if (it === retainedInit) initPendingInQueue = false }
                    } else {
                        queue.poll()
                    }
                } else {
                    var polled: ByteArray? = null
                    var waitedMs = 0L
                    while (polled == null && !closed.get()) {
                        if (endOfStream.get() && queue.isEmpty()) break
                        // Serve a pending init replay before touching the queue.
                        headQueue.pollFirst()?.let {
                            polled = it
                            if (it === retainedInit) initPendingInQueue = false
                        }
                        if (polled == null) {
                            polled = try {
                                queue.poll(250, TimeUnit.MILLISECONDS)
                            } catch (error: InterruptedException) {
                                Thread.currentThread().interrupt()
                                throw IOException("Interrupted while waiting for SABR media", error)
                            }
                        }
                        if (polled == null) {
                            waitedMs += 250
                            if (paused) {
                                // Deliberate pause: delivery is expected to stop.
                                // Keep refreshing the budget so resume starts clean.
                                lastDataAtMs = System.currentTimeMillis()
                                continue
                            }
                            val idleMs = System.currentTimeMillis() - lastDataAtMs
                            // Producer died without EOS (e.g. follow-up loop broke on error):
                            // fail loudly so ExoPlayer raises a real error instead of hanging in
                            // STATE_BUFFERING forever.
                            if (idleMs >= STALL_TIMEOUT_MS) {
                                throw IOException(
                                    "SABR media stall: no segments for ${idleMs}ms " +
                                        "(eos=${endOfStream.get()}, closed=${closed.get()}, paused=$paused)"
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
        headQueue.clear()
        initPendingInQueue = false
        currentChunk = null
        currentOffset = 0
    }

    /**
     * Seek support: drop all queued media so extractors re-opening after a seek
     * read a clean init + fragments-from-the-new-playhead sequence instead of
     * stale bytes from the old read cursor. Keeps the retained init segment.
     */
    fun resetForSeek() {
        queue.clear()
        headQueue.clear()
        currentChunk = null
        currentOffset = 0
        endOfStream.set(false)
        initPendingInQueue = false
        replayInitForReopen()
        lastDataAtMs = System.currentTimeMillis()
    }

    fun reset() {
        queue.clear()
        headQueue.clear()
        initPendingInQueue = false
        currentChunk = null
        currentOffset = 0
        closed.set(false)
        endOfStream.set(false)
        paused = false
        lastDataAtMs = System.currentTimeMillis()
    }
}
