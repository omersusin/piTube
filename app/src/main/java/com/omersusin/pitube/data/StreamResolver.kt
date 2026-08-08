package com.omersusin.pitube.data

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import com.omersusin.pitube.data.CookieDownloader.Companion.initWithCookies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList

@UnstableApi
object StreamResolver {
    private const val TAG = "StreamResolver"

    data class Resolved(
        val title: String,
        val description: String,
        val uploader: String,
        val uploaderUrl: String,
        val videoUrl: String?,
        val audioUrl: String?,
        val hlsUrl: String?
    ) {
        val playUrl: String? get() = videoUrl ?: hlsUrl
        val downloadUrl: String? get() = videoUrl
    }

    suspend fun resolve(videoId: String, context: Context): Resolved? = withContext(Dispatchers.IO) {
        try {
            initWithCookies(context)
            val videoUrl = "https://www.youtube.com/watch?v=$videoId"
            val extractor = ServiceList.YouTube.getStreamExtractor(videoUrl)
            extractor.fetchPage()

            val title = extractor.name ?: ""
            val description = extractor.description?.textContent ?: extractor.description?.toString() ?: ""
            val uploader = extractor.uploaderName ?: ""
            val uploaderUrl = extractor.uploaderUrl ?: ""
            val hlsUrl = extractor.hlsUrl

            // Pick best video stream (highest quality <= 1080p)
            val videoStreams = extractor.videoStreams ?: emptyList()
            val bestVideo = videoStreams
                .filter { it.videoOnly && !it.url.isNullOrBlank() }
                .filter { it.quality.uppercase().let { q -> q.contains("1080") || q.contains("720") } }
                .maxByOrNull { it.quality?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0 }
                ?: videoStreams
                    .filter { it.videoOnly && !it.url.isNullOrBlank() }
                    .maxByOrNull { it.quality?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0 }

            // Pick best audio stream (highest bitrate)
            val audioStreams = extractor.audioStreams ?: emptyList()
            val bestAudio = audioStreams
                .filter { !it.url.isNullOrBlank() }
                .maxByOrNull { it.bitrate }

            val resolved = Resolved(
                title = title,
                description = description,
                uploader = uploader,
                uploaderUrl = uploaderUrl,
                videoUrl = bestVideo?.url,
                audioUrl = bestAudio?.url,
                hlsUrl = hlsUrl
            )

            Log.d(TAG, "Resolved $videoId: video=${resolved.videoUrl != null} audio=${resolved.audioUrl != null} hls=${resolved.hlsUrl != null}")
            resolved
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving $videoId: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    fun buildMediaSource(context: Context, resolved: Resolved): MediaSource? {
        val videoUrl = resolved.videoUrl
        val audioUrl = resolved.audioUrl
        val hlsUrl = resolved.hlsUrl

        return try {
            val dataSourceFactory = ChunkedStreamDataSource.Factory()

            // If we have both video and audio URLs, merge them
            if (!videoUrl.isNullOrBlank() && !audioUrl.isNullOrBlank()) {
                val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(videoUrl))
                val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(audioUrl))
                return MergingMediaSource(videoSource, audioSource)
            }

            // If we have only video URL (combined format or video-only)
            if (!videoUrl.isNullOrBlank()) {
                val mediaItem = MediaItem.fromUri(videoUrl)
                return ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            }

            // Fallback to HLS
            if (!hlsUrl.isNullOrBlank()) {
                val mediaItem = MediaItem.fromUri(hlsUrl)
                return ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
