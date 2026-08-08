package com.omersusin.pitube.data

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList

object StreamResolver {
    enum class Strategy { HLS, PROGRESSIVE, MERGED, NONE }
    data class Resolved(val strategy: Strategy, val hlsUrl: String?, val playUrl: String?, val videoOnlyUrl: String?, val downloadUrl: String?, val audioUrl: String?, val title: String, val description: String, val uploader: String, val uploaderUrl: String)

    private fun qualityOf(res: String?) = res?.filter { it.isDigit() }?.toIntOrNull() ?: 0
    fun hasPlayable(r: Resolved?) = r != null && (r.hlsUrl != null || r.playUrl != null || (r.videoOnlyUrl != null && r.audioUrl != null))

    suspend fun resolve(videoId: String): Resolved? = withContext(Dispatchers.IO) {
        var r: Resolved? = null
        try {
            val ex = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            ex.fetchPage()
            val hls = runCatching { ex.hlsUrl }.getOrNull()?.takeIf { it.isNotBlank() }
            val withAudio = runCatching { ex.videoStreams }.getOrNull() ?: emptyList()
            val videoOnly = runCatching { ex.videoOnlyStreams }.getOrNull() ?: emptyList()
            val audios = runCatching { ex.audioStreams }.getOrNull() ?: emptyList()
            val prog = withAudio.maxByOrNull { qualityOf(it.resolution) }
            val bestOnly = videoOnly.maxByOrNull { qualityOf(it.resolution) }
            val bestAudio = audios.maxByOrNull { it.averageBitrate }
            r = Resolved(Strategy.HLS, hls, prog?.content, bestOnly?.content, bestOnly?.content ?: prog?.content, bestAudio?.content,
                runCatching { ex.name }.getOrNull() ?: "", runCatching { ex.description?.content }.getOrNull() ?: "",
                runCatching { ex.uploaderName }.getOrNull() ?: "", runCatching { ex.uploaderUrl }.getOrNull() ?: "")
        } catch (e: Exception) { e.printStackTrace() }
        if (!hasPlayable(r)) {
            val p = InnerTubeFeed.fetchPlayer(videoId)
            if (good(p)) r = Resolved(Strategy.HLS, p!!.hls, p.progressive?.url, p.videoOnly?.url, p.videoOnly?.url ?: p.progressive?.url, p.audio?.url, "", "", "", "")
        }
        if (!hasPlayable(r)) {
            try {
                val info = PipedApiService.create().getStreams(videoId)
                val vid = info.videoStreams.filter { !it.videoOnly && it.mimeType.contains("mp4", true) }.maxByOrNull { qualityOf(it.quality) }
                val vo = info.videoStreams.filter { it.videoOnly }.maxByOrNull { qualityOf(it.quality) }
                val au = info.audioStreams.maxByOrNull { qualityOf(it.quality) }
                if (info.hls != null || vid != null || vo != null) r = Resolved(Strategy.HLS, info.hls, vid?.url, vo?.url, vo?.url ?: vid?.url, au?.url, info.title, info.description, info.uploader, info.uploaderUrl)
            } catch (e: Exception) { }
        }
        r
    }
    private fun good(p: InnerTubeFeed.PlayerData?) = p != null && (p.hls != null || p.progressive != null || p.videoOnly != null)

    fun buildMediaSource(context: android.content.Context, resolved: Resolved): MediaSource? {
        val factory: DataSource.Factory = ChunkedStreamDataSource.factory()
        resolved.hlsUrl?.let { hls ->
            val item = MediaItem.Builder().setUri(hls).setMimeType(MimeTypes.APPLICATION_M3U8).build()
            return HlsMediaSource.Factory(factory).createMediaSource(item)
        }
        resolved.playUrl?.let { url -> return ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(url)) }
        val vo = resolved.videoOnlyUrl; val au = resolved.audioUrl
        if (vo != null && au != null) {
            return MergingMediaSource(ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(vo)), ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(au)))
        }
        return null
    }
}
