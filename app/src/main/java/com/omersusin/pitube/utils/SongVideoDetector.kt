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

private val SONG_HINT_PATTERN =
    Regex("\\((official|audio|lyrics?|visualizer|hq)\\)|\\b(feat\\.?|ft\\.)\\b", RegexOption.IGNORE_CASE)

private val LABEL_HINT_PATTERN = Regex("records?|entertainment|music", RegexOption.IGNORE_CASE)

/**
 * Combined detector: strict signals win outright; otherwise a score model over
 * weak signals decides. Replaces the old catch-all that showed the lyrics
 * button for essentially every 60–600s video with any channel name.
 *
 * Score model (threshold ≥ 3):
 *   +1 duration in the typical song window (60–600s)
 *   +2 "artist - track" separator convention
 *   +2 song-ish parenthetical/hint ((official|audio|lyrics…)/feat./ft.)
 *   +1 record-label-ish channel name (Records/Music/Entertainment)
 */
fun isSongVideoLenient(video: Video, streamInfo: StreamInfo?): Boolean {
    if (isSongVideo(video, streamInfo)) return true
    if (video.isLive || video.isShort || video.isUpcoming) return false
    val name = (streamInfo?.name ?: video.title).lowercase()
    val uploader = (streamInfo?.uploaderName ?: video.channelName).lowercase()

    var score = 0
    if (video.duration in 60..600) score += 1
    if (name.contains(" - ")) score += 2
    if (SONG_HINT_PATTERN.containsMatchIn(name)) score += 2
    if (LABEL_HINT_PATTERN.containsMatchIn(uploader)) score += 1
    return score >= 3
}
