package com.omersusin.pitube.data

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@UnstableApi
object StreamResolver {
    private const val TAG = "StreamResolver"

    data class VideoFormat(
        val height: Int,
        val label: String,
        val url: String,
        val mime: String,
        val fps: Int = 0,
        val contentLength: Long = 0L,
        val isProgressive: Boolean = false
    )

    data class Resolved(
        val title: String,
        val description: String,
        val uploader: String,
        val uploaderUrl: String,
        val videoUrl: String?,
        val audioUrl: String?,
        val hlsUrl: String?,
        val videoFormats: List<VideoFormat> = emptyList()
    ) {
        val playUrl: String? get() = videoUrl ?: hlsUrl
        val downloadUrl: String? get() = videoUrl
        val bestQualityLabel: String get() = videoFormats.firstOrNull()?.label ?: "Auto"

        /**
         * Returns a copy whose videoUrl is the format matching [height] (or the
         * best format when null). Falls back to this instance if no match.
         */
        fun withVideoQuality(height: Int?): Resolved {
            if (height == null) return this
            val format = videoFormats.firstOrNull { it.height == height } ?: return this
            return copy(videoUrl = format.url)
        }
    }

    private val formatSelector: (JSONObject) -> Format? = { fmt ->
        val mime = fmt.optString("mimeType")
        val itag = fmt.optInt("itag", -1)
        val type = if (mime.startsWith("video/")) "video" else if (mime.startsWith("audio/")) "audio" else null
        if (type == null || fmt.optString("url").isBlank()) null
        else Format(
            url = fmt.optString("url"),
            mime = mime,
            itag = itag,
            bitrate = fmt.optInt("bitrate", 0),
            width = fmt.optInt("width", 0),
            height = fmt.optInt("height", 0),
            contentLength = fmt.optLong("contentLength", 0L),
            fps = fmt.optInt("fps", 0),
            type = type
        )
    }

    suspend fun resolve(videoId: String, context: Context): Resolved? = withContext(Dispatchers.IO) {
        try {
            val response = InnerTubeClient.player(context, videoId)
            val streamingData = response.streamingData
            if (streamingData == null) {
                Log.e(TAG, "No streamingData for $videoId")
                return@withContext null
            }
            val details = response.videoDetails
            val title = details?.optString("title").orEmpty()
            val uploader = details?.optString("author").orEmpty()
            val channelId = details?.optString("channelId").orEmpty()
            val desc = details?.optString("shortDescription").orEmpty()

            val formats = mutableListOf<Format>()
            streamingData.optJSONArray("formats")?.let { arr ->
                for (i in 0 until arr.length()) formatSelector(arr.optJSONObject(i))?.let { formats.add(it) }
            }
            streamingData.optJSONArray("adaptiveFormats")?.let { arr ->
                for (i in 0 until arr.length()) formatSelector(arr.optJSONObject(i))?.let { formats.add(it) }
            }

            val videoFormats = formats.filter { it.type == "video" }
            val audioFormats = formats.filter { it.type == "audio" }

            // Prefer DASH video-only formats, then progressive (combined)
            val dashVideo = videoFormats
                .filter { it.mime.startsWith("video/mp4") }
                .filter { it.height in 1..1080 }
                .maxByOrNull { it.height }
            val bestVideo = dashVideo
                ?: videoFormats
                    .filter { it.mime.startsWith("video/webm") }
                    .filter { it.height in 1..1080 }
                    .maxByOrNull { it.height }
                ?: videoFormats.maxByOrNull { it.height }

            val bestAudio = audioFormats
                .filter { it.mime.startsWith("audio/mp4") }
                .maxByOrNull { it.bitrate }
                ?: audioFormats.maxByOrNull { it.bitrate }

            // Quality list: DASH mp4 first, then progressive (combined), dedup by height
            val sortedVideo = videoFormats
                .sortedByDescending { it.height }
                .sortedWith(compareByDescending<Format> { it.mime.startsWith("video/mp4") }.thenByDescending { it.height })
            val seenHeights = mutableSetOf<Int>()
            val qualityList = sortedVideo.mapNotNull { f ->
                if (f.height <= 0 || !seenHeights.add(f.height)) null
                else VideoFormat(
                    height = f.height,
                    label = "${f.height}p",
                    url = f.url,
                    mime = f.mime,
                    fps = f.fps,
                    contentLength = f.contentLength,
                    isProgressive = f.mime.contains("video/mp4") && f.bitrate > 0 && f.height in 1..1080
                )
            }

            val hlsUrl = streamingData.optString("hlsManifestUrl").takeIf { it.isNotBlank() }

            val resolved = Resolved(
                title = title,
                description = desc,
                uploader = uploader,
                uploaderUrl = channelId.let { "https://www.youtube.com/channel/$it" },
                videoUrl = bestVideo?.url,
                audioUrl = bestAudio?.url,
                hlsUrl = hlsUrl,
                videoFormats = qualityList
            )

            Log.d(TAG, "Resolved $videoId: video=${resolved.videoUrl != null}(${bestVideo?.height}p) audio=${resolved.audioUrl != null} hls=${resolved.hlsUrl != null}")
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

    private data class Format(
        val url: String,
        val mime: String,
        val itag: Int,
        val bitrate: Int,
        val width: Int,
        val height: Int,
        val contentLength: Long,
        val fps: Int,
        val type: String
    )
}
