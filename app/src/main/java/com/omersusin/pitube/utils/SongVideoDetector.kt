package com.omersusin.pitube.utils

import com.omersusin.pitube.data.model.Video
import org.schabi.newpipe.extractor.stream.StreamInfo

fun isSongVideo(video: Video, streamInfo: StreamInfo?): Boolean {
    if (video.isMusic) return true
    if (video.isLive || video.isShort || video.isUpcoming) return false
    val uploader = (streamInfo?.uploaderName ?: video.channelName).lowercase()
    if (uploader.contains(" - topic") || uploader.contains("vevo")) return true
    runCatching {
        val cat = streamInfo?.let { si ->
            try { si.javaClass.getMethod("getCategory").invoke(si) as? String } catch (_: Exception) { null }
                ?: try { si.javaClass.getDeclaredField("category").let { it.isAccessible = true; it.get(si) as? String } } catch (_: Exception) { null }
        }?.lowercase()
        if (cat?.contains("music") == true) return true
    }
    val name = (streamInfo?.name ?: video.title).lowercase()
    if (name.contains("official music video") || name.contains("official audio") || name.contains("lyric video") || name.contains("official video") || name.contains("(official)")) return true
    val tags = runCatching { streamInfo?.tags?.joinToString(" ")?.lowercase() }.getOrNull() ?: ""
    if (tags.contains("music")) return true
    return false
}

fun isSongVideoLenient(video: Video, streamInfo: StreamInfo?): Boolean {
    if (isSongVideo(video, streamInfo)) return true
    if (video.isLive || video.isShort || video.isUpcoming) return false
    val name = (streamInfo?.name ?: video.title).lowercase()
    if (name.contains(" - ") && video.duration in 60..600) return true
    return video.duration in 60..600 && (streamInfo?.uploaderName ?: video.channelName).isNotBlank()
}
