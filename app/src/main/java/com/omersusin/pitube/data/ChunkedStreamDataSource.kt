package com.omersusin.pitube.data

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener

@UnstableApi
class ChunkedStreamDataSource private constructor(
    private val delegate: DefaultHttpDataSource,
) : DataSource {

    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedStreamDataSource(
            DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(READ_TIMEOUT_MS)
                .setAllowCrossProtocolRedirects(true)
                .createDataSource()
        )
    }

    companion object {
        private const val CHUNK_SIZE_BYTES = 10L * 1024 * 1024
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val UNSET = C.LENGTH_UNSET.toLong()

        private val CONTENT_RANGE_REGEX = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""")

        private fun shouldChunk(uri: Uri): Boolean {
            if (uri.host?.endsWith(".googlevideo.com") != true) return false
            return !uri.query.isNullOrEmpty()
        }

        fun uaForPlaybackUri(uri: Uri): String = when (uri.getQueryParameter("c")) {
            "ANDROID", "ANDROID_VR" -> "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L) gzip"
            "IOS" -> "com.google.ios.youtube/21.02.3 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X)"
            else -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        }
    }

    private var currentSpec: DataSpec? = null
    private var chunked = false
    private var position = 0L
    private var requestedEnd = UNSET
    private var totalLength = UNSET
    private var chunkRemaining = 0L
    private var chunkProgressed = false

    override fun addTransferListener(transferListener: TransferListener) {
        delegate.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        delegate.setRequestProperty("User-Agent", uaForPlaybackUri(dataSpec.uri))
        currentSpec = dataSpec
        position = dataSpec.position
        chunked = shouldChunk(dataSpec.uri)
        if (!chunked) {
            return delegate.open(dataSpec)
        }

        requestedEnd =
            if (dataSpec.length != UNSET) dataSpec.position + dataSpec.length else UNSET
        totalLength = UNSET
        openChunk()

        return when {
            dataSpec.length != UNSET ->
                if (totalLength != UNSET) {
                    minOf(dataSpec.length, totalLength - dataSpec.position)
                } else {
                    dataSpec.length
                }
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

    override fun getUri(): Uri? = delegate.uri ?: currentSpec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders

    override fun close() {
        currentSpec = null
        chunked = false
        chunkRemaining = 0
        delegate.close()
    }

    private fun effectiveEnd(): Long = when {
        requestedEnd != UNSET && totalLength != UNSET -> minOf(requestedEnd, totalLength)
        requestedEnd != UNSET -> requestedEnd
        else -> totalLength
    }

    private fun openChunk() {
        val spec = checkNotNull(currentSpec)
        val end = effectiveEnd()
        val maxLen = if (end != UNSET) end - position else Long.MAX_VALUE
        val len = minOf(CHUNK_SIZE_BYTES, maxLen)
        chunkProgressed = false
        if (len <= 0L) {
            chunkRemaining = 0
            return
        }

        val chunkSpec = spec.buildUpon().setPosition(position).setLength(len).build()
        try {
            delegate.open(chunkSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode == 416) {
                chunkRemaining = 0
                return
            }
            throw e
        }

        val contentRange = delegate.responseHeaders["Content-Range"]?.firstOrNull()
        val match = contentRange?.let { CONTENT_RANGE_REGEX.find(it) }
        if (match != null) {
            val (_, rangeEnd, total) = match.destructured
            chunkRemaining = rangeEnd.toLong() - position + 1
            total.toLongOrNull()?.let { totalLength = it }
        } else {
            chunkRemaining = len
        }
    }
}
