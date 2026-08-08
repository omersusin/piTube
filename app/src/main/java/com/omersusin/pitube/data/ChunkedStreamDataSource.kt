package com.omersusin.pitube.data

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener

class ChunkedStreamDataSource(private val delegate: DefaultHttpDataSource) : DataSource {
    private var currentSpec: DataSpec? = null
    private var position = 0L
    private var chunked = false
    private var requestedEnd = UNSET
    private var totalLength = UNSET
    private var chunkRemaining = 0L
    private var chunkProgressed = false

    override fun open(dataSpec: DataSpec): Long {
        delegate.setRequestProperty("User-Agent", uaFor(dataSpec.uri))
        currentSpec = dataSpec
        position = dataSpec.position
        chunked = shouldChunk(dataSpec.uri)
        if (!chunked) return delegate.open(dataSpec)
        requestedEnd = if (dataSpec.length != UNSET) dataSpec.position + dataSpec.length else UNSET
        totalLength = UNSET
        openChunk()
        return when {
            dataSpec.length != UNSET -> if (totalLength != UNSET) minOf(dataSpec.length, totalLength - dataSpec.position) else dataSpec.length
            totalLength != UNSET -> totalLength - dataSpec.position
            else -> UNSET
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!chunked) return delegate.read(buffer, offset, length)
        if (length == 0) return 0
        if (chunkRemaining <= 0L) {
            val end = effectiveEnd()
            if (end != UNSET && position >= end) return C.RESULT_END_OF_INPUT
            delegate.close()
            openChunk()
            if (chunkRemaining <= 0L) return C.RESULT_END_OF_INPUT
        }
        val toRead = minOf(length.toLong(), chunkRemaining).toInt()
        val read = delegate.read(buffer, offset, toRead)
        if (read == C.RESULT_END_OF_INPUT) {
            if (!chunkProgressed) return C.RESULT_END_OF_INPUT
            chunkRemaining = 0
            return read(buffer, offset, length)
        }
        chunkProgressed = true
        position += read
        chunkRemaining -= read
        return read
    }

    override fun addTransferListener(transferListener: TransferListener) {
        delegate.addTransferListener(transferListener)
    }

    private fun effectiveEnd() = if (requestedEnd != UNSET) requestedEnd else if (totalLength != UNSET) totalLength else UNSET

    private fun openChunk() {
        val spec = currentSpec ?: return
        val end = effectiveEnd()
        val maxLen = if (end != UNSET) end - position else Long.MAX_VALUE
        val len = minOf(CHUNK, maxLen)
        chunkProgressed = false
        if (len <= 0L) { chunkRemaining = 0; return }
        try {
            delegate.open(spec.buildUpon().setPosition(position).setLength(len).build())
        } catch (e: androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode == 416) { chunkRemaining = 0; return }
            throw e
        }
        val cr = delegate.responseHeaders["Content-Range"]?.firstOrNull()
        val match = cr?.let { RANGE_RE.find(it) }
        if (match != null) {
            val (_, rangeEnd, total) = match.destructured
            chunkRemaining = rangeEnd.toLong() - position + 1
            total.toLongOrNull()?.let { totalLength = it }
        } else chunkRemaining = len
    }

    override fun getUri(): Uri? = currentSpec?.uri
    override fun close() { delegate.close() }

    companion object {
        private const val CHUNK = 10L * 1024 * 1024
        private const val UNSET = C.LENGTH_UNSET.toLong()
        private val RANGE_RE = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""")
        fun shouldChunk(uri: Uri) = uri.host?.endsWith(".googlevideo.com") == true && !uri.query.isNullOrEmpty()
        fun uaFor(uri: Uri): String = when (uri.getQueryParameter("c")) {
            "ANDROID", "ANDROID_VR" -> "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip"
            "IOS" -> "com.google.ios.youtube/19.28.1 (iPhone; CPU iOS 17_0 like Mac OS X)"
            else -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        }
        fun factory(): DataSource.Factory = DataSource.Factory {
            ChunkedStreamDataSource(DefaultHttpDataSource.Factory().setConnectTimeoutMs(15000).setReadTimeoutMs(15000).createDataSource())
        }
    }
}
