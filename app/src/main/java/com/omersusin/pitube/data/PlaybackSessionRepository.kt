package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import java.io.File

data class PlaybackSession(
    val videoId: String,
    val positionMs: Long,
    val savedAt: Long
)

object PlaybackSessionRepository {
    private const val SESSION_FILE = "playback_session.json"
    private val gson = Gson()

    private fun getSessionFile(context: Context) = File(context.filesDir, SESSION_FILE)

    fun saveSession(context: Context, videoId: String, positionMs: Long) {
        val session = PlaybackSession(videoId, positionMs, System.currentTimeMillis())
        val file = getSessionFile(context)
        file.writeText(gson.toJson(session))
    }

    fun getSession(context: Context): PlaybackSession? {
        val file = getSessionFile(context)
        if (!file.exists()) return null
        
        return try {
            val json = file.readText()
            gson.fromJson(json, PlaybackSession::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
