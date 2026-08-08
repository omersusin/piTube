package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import java.io.File

object SessionResume {
    data class Snapshot(val video: VideoItem, val position: Long, val ts: Long)
    private val gson = Gson()

    fun save(context: Context, video: VideoItem, position: Long) {
        File(context.filesDir, "session.json").writeText(gson.toJson(Snapshot(video, position, System.currentTimeMillis())))
    }
    fun load(context: Context): Snapshot? = try {
        val f = File(context.filesDir, "session.json")
        if (!f.exists()) null else gson.fromJson(f.readText(), Snapshot::class.java)
    } catch (e: Exception) { null }
    fun clear(context: Context) { File(context.filesDir, "session.json").delete() }
}
