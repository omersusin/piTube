package com.omersusin.pitube.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import android.util.Log

@Serializable
data class PlaybackSession(
    val videoId: String,
    val positionMs: Long,
    val savedAt: Long
)

class PlaybackSessionRepository(context: Context) {
    companion object {
        private const val TAG = "PlaybackSession"
        private const val FILE_NAME = "playback_session.json"
    }

    private val sessionFile = File(context.filesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun save(videoId: String, positionMs: Long) {
        if (videoId.isBlank()) return
        try {
            val session = PlaybackSession(
                videoId = videoId,
                positionMs = positionMs.coerceAtLeast(0L),
                savedAt = System.currentTimeMillis()
            )
            sessionFile.writeText(json.encodeToString(session))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save playback session", e)
        }
    }

    fun load(): PlaybackSession? {
        return try {
            if (!sessionFile.exists()) return null
            json.decodeFromString<PlaybackSession>(sessionFile.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load playback session", e)
            null
        }
    }

    fun clear() {
        try {
            sessionFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear playback session", e)
        }
    }
}
