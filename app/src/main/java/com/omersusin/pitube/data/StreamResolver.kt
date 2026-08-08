package com.omersusin.pitube.data

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList

object StreamResolver {
    private const val STREAM_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    enum class Strategy { HLS, PROGRESSIVE, MERGED, NONE }

    data class Resolved(
        val strategy: Strategy,
        val hlsUrl: String?,
        val playUrl: String?,
        val videoOnlyUrl: String?,
        val downloadUrl: String?,
        val audioUrl: String?,
        val title: String,
        val description: String,
        val uploader: String,
        val uploaderUrl: String
    )

    private fun qualityOf(res: String?) = res?.filter { it.isDigit() }?.toIntOrNull() ?: 0

    suspend fun resolve(videoId: String): Resolved? = withContext(Dispatchers.IO) {
        try {
            val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()

            val hls = runCatching { extractor.hlsUrl }.getOrNull()?.takeIf { it.isNotBlank() }
            val withAudio = runCatching { extractor.videoStreams }.getOrNull() ?: emptyList()
            val videoOnly = runCatching { extractor.videoOnlyStreams }.getOrNull() ?: emptyList()
            val audios = runCatching { extractor.audioStreams }.getOrNull() ?: emptyList()

            val progressive = withAudio.maxByOrNull { qualityOf(it.resolution) }
            val bestOnly = videoOnly.maxByOrNull { qualityOf(it.resolution) }
            val bestAudio = audios.maxByOrNull { it.averageBitrate }

            val strategy = when {
                hls != null -> Strategy.HLS
                progressive?.content != null -> Strategy.PROGRESSIVE
                bestOnly?.content != null && bestAudio?.content != null -> Strategy.MERGED
                else -> Strategy.NONE
            }

            Resolved(
                strategy = strategy, hlsUrl = hls, playUrl = progressive?.content,
                videoOnlyUrl = bestOnly?.content, downloadUrl = bestOnly?.content ?: progressive?.content,
                audioUrl = bestAudio?.content,
                title = runCatching { extractor.name }.getOrNull() ?: "",
                description = runCatching { extractor.description?.content }.getOrNull() ?: "",
                uploader = runCatching { extractor.uploaderName }.getOrNull() ?: "",
                uploaderUrl = runCatching { extractor.uploaderUrl }.getOrNull() ?: ""
            )
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    fun buildMediaSource(context: Context, resolved: Resolved): MediaSource? {
        val dataSourceFactory: DataSource.Factory = DefaultHttpDataSource.Factory()
            .setUserAgent(STREAM_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000).setReadTimeoutMs(15_000)

        resolved.hlsUrl?.let { hls ->
            val item = MediaItem.Builder().setUri(hls).setMimeType(MimeTypes.APPLICATION_M3U8).build()
            return HlsMediaSource.Factory(dataSourceFactory).createMediaSource(item)
        }
        resolved.playUrl?.let { url ->
            return ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(url))
        }
        val videoOnly = resolved.videoOnlyUrl
        val audio = resolved.audioUrl
        if (videoOnly != null && audio != null) {
            val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(videoOnly))
            val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(audio))
            return MergingMediaSource(videoSource, audioSource)
        }
        return null
    }
}
