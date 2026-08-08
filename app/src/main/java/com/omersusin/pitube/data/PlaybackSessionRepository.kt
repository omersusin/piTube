package com.omersusin.pitube.data
import android.content.Context
import com.google.gson.Gson
import java.io.File
data class PlaybackSession(val video: VideoItem, val positionMs: Long, val savedAt: Long)
class PlaybackSessionRepository(context: Context) {
    private val file = File(context.filesDir, "playback_session.json"); private val gson = Gson()
    fun save(video: VideoItem, positionMs: Long) { try { file.writeText(gson.toJson(PlaybackSession(video, positionMs, System.currentTimeMillis()))) } catch (e: Exception) {} }
    fun load(): PlaybackSession? = try { if (!file.exists()) null else gson.fromJson(file.readText(), PlaybackSession::class.java) } catch (e: Exception) { null }
    fun clear() { try { file.delete() } catch (e: Exception) {} }
}
