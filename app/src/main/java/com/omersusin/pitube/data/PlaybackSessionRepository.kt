package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import java.io.File

object PlaybackSessionRepository {
    private const val SESSION_FILE = "playback_session.json"
    private val gson = Gson()
    
    data class PlaybackSession(
        val videoId: String,
        val position: Long,
        val timestamp: Long
    )
    
    fun saveSession(context: Context, videoId: String, position: Long) {
        val session = PlaybackSession(videoId, position, System.currentTimeMillis())
        File(context.filesDir, SESSION_FILE).writeText(gson.toJson(session))
    }
    
    fun getSession(context: Context): PlaybackSession? {
        val file = File(context.filesDir, SESSION_FILE)
        if (!file.exists()) return null
        
        return try {
            gson.fromJson(file.readText(), PlaybackSession::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    fun clearSession(context: Context) {
        File(context.filesDir, SESSION_FILE).delete()
    }
    
    fun isSessionFresh(context: Context, maxAgeHours: Int = 24): Boolean {
        val session = getSession(context) ?: return false
        val ageHours = (System.currentTimeMillis() - session.timestamp) / (1000 * 60 * 60)
        return ageHours <= maxAgeHours
    }
}
