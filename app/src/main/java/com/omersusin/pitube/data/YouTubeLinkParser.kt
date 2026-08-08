package com.omersusin.pitube.data

import android.net.Uri

data class ParsedYouTubeLink(
    val videoId: String? = null,
    val playlistId: String? = null,
    val isMusicLink: Boolean = false
)

object YouTubeLinkParser {
    private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    private val PLAYLIST_ID = Regex("[A-Za-z0-9_-]{2,}")
    private val VIDEO_PATH_ROOTS = setOf("shorts", "live", "embed", "v")
    private val TRAILING_PUNCTUATION = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'')

    fun parseFromSharedText(text: String): ParsedYouTubeLink? =
        text.split(' ', '\n', '\r', '\t', '<', '>', '"')
            .asSequence()
            .mapNotNull { token -> parse(token.trim().trimEnd(*TRAILING_PUNCTUATION)) }
            .firstOrNull()

    fun parse(input: String): ParsedYouTubeLink? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null

        val candidate = when {
            text.startsWith("http://", ignoreCase = true) ||
                text.startsWith("https://", ignoreCase = true) -> text
            text.contains("youtube.com/", ignoreCase = true) ||
                text.contains("youtu.be/", ignoreCase = true) -> "https://$text"
            else -> return null
        }

        val uri = try { Uri.parse(candidate) } catch (e: Exception) { return null }
        val host = uri.host?.lowercase() ?: return null
        val isYouTubeHost = host == "youtu.be" ||
            host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com") ||
            host == "music.youtube.com" || host.endsWith(".music.youtube.com")
        if (!isYouTubeHost) return null

        val segments = try { uri.pathSegments } catch (e: Exception) { emptyList<String>() }

        val videoId = when {
            host == "youtu.be" -> segments.firstOrNull()
            segments.firstOrNull() == "watch" -> uri.getQueryParameter("v")
            segments.firstOrNull() in VIDEO_PATH_ROOTS -> segments.getOrNull(1)
            else -> null
        }?.takeIf { it != "videoseries" && VIDEO_ID.matches(it) }

        val playlistId = try {
            uri.getQueryParameter("list")?.takeIf { PLAYLIST_ID.matches(it) }
        } catch (e: Exception) { null }

        if (videoId == null && playlistId == null) return null
        return ParsedYouTubeLink(
            videoId = videoId,
            playlistId = playlistId,
            isMusicLink = host == "music.youtube.com"
        )
    }

    fun isYouTubeUrl(url: String): Boolean {
        val text = url.trim()
        if (text.isEmpty()) return false
        val candidate = when {
            text.startsWith("http://", ignoreCase = true) ||
                text.startsWith("https://", ignoreCase = true) -> text
            text.contains("youtube.com/", ignoreCase = true) ||
                text.contains("youtu.be/", ignoreCase = true) -> "https://$text"
            else -> return false
        }
        val uri = try { Uri.parse(candidate) } catch (e: Exception) { return false }
        val host = uri.host?.lowercase() ?: return false
        return host == "youtu.be" ||
            host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com") ||
            host == "music.youtube.com" || host.endsWith(".music.youtube.com")
    }
}
