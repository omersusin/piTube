package com.omersusin.pitube.data

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

    fun hasPlayable(r: Resolved?) = r != null && (r.hlsUrl != null || r.playUrl != null || (r.videoOnlyUrl != null && r.audioUrl != null))

    suspend fun resolve(videoId: String): Resolved? = withContext(Dispatchers.IO) {
        var r: Resolved? = null

        // 1) NewPipe Extractor (on-device)
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
            r = Resolved(
                strategy = Strategy.HLS, hlsUrl = hls, playUrl = progressive?.content,
                videoOnlyUrl = bestOnly?.content, downloadUrl = bestOnly?.content ?: progressive?.content,
                audioUrl = bestAudio?.content,
                title = runCatching { extractor.name }.getOrNull() ?: "",
                description = runCatching { extractor.description?.content }.getOrNull() ?: "",
                uploader = runCatching { extractor.uploaderName }.getOrNull() ?: "",
                uploaderUrl = runCatching { extractor.uploaderUrl }.getOrNull() ?: ""
            )
        } catch (e: Exception) { e.printStackTrace() }

        // 2) InnerTube ANDROID player (unciphered urls)
        if (!hasPlayable(r)) {
            val p = InnerTubeFeed.fetchPlayer(videoId)
            if (p != null && (p.hls != null || p.progressive != null || p.videoOnly != null)) {
                r = Resolved(Strategy.HLS, p.hls, p.progressive?.url, p.videoOnly?.url,
                    p.videoOnly?.url ?: p.progressive?.url, p.audio?.url, "", "", "", "")
            }
        }

        // 3) Piped instances
        if (!hasPlayable(r)) {
            try {
                val info = PipedApiService.create().getStreams(videoId)
                val vid = info.videoStreams.filter { !it.videoOnly && it.mimeType.contains("mp4", true) }.maxByOrNull { qualityOf(it.quality) }
                val vo = info.videoStreams.filter { it.videoOnly }.maxByOrNull { qualityOf(it.quality) }
                val au = info.audioStreams.maxByOrNull { qualityOf(it.quality) }
                if (info.hls != null || vid != null || vo != null) {
                    r = Resolved(Strategy.HLS, info.hls, vid?.url, vo?.url, vo?.url ?: vid?.url, au?.url,
                        info.title, info.description, info.uploader, info.uploaderUrl)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
        r
    }

    fun buildMediaSource(context: android.content.Context, resolved: Resolved): androidx.media3.exoplayer.source.MediaSource? {
        val dataSourceFactory: androidx.media3.datasource.DataSource.Factory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(STREAM_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)

        resolved.hlsUrl?.let { hls ->
            val item = androidx.media3.common.MediaItem.Builder().setUri(hls).setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8).build()
            return androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSourceFactory).createMediaSource(item)
        }
        resolved.playUrl?.let { url ->
            return androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(androidx.media3.common.MediaItem.fromUri(url))
        }
        val videoOnly = resolved.videoOnlyUrl
        val audio = resolved.audioUrl
        if (videoOnly != null && audio != null) {
            val videoSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(androidx.media3.common.MediaItem.fromUri(videoOnly))
            val audioSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(androidx.media3.common.MediaItem.fromUri(audio))
            return androidx.media3.exoplayer.source.MergingMediaSource(videoSource, audioSource)
        }
        return null
    }
}
