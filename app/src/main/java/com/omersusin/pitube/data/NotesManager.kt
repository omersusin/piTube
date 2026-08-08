package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class VideoNote(val timeMs: Long, val text: String)

object NotesManager {
    private const val FILE_NAME = "notes.json"
    private val gson = Gson()

    private fun loadAll(context: Context): MutableMap<String, List<VideoNote>> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, List<VideoNote>>>() {}.type
            gson.fromJson(file.readText(), type) ?: mutableMapOf()
        } catch (e: Exception) { mutableMapOf() }
    }

    fun getNotes(context: Context, videoId: String): List<VideoNote> = loadAll(context)[videoId] ?: emptyList()

    fun addNote(context: Context, videoId: String, note: VideoNote) {
        val all = loadAll(context)
        all[videoId] = (all[videoId] ?: emptyList()) + note
        File(context.filesDir, FILE_NAME).writeText(gson.toJson(all))
    }

    fun deleteNote(context: Context, videoId: String, index: Int) {
        val all = loadAll(context)
        val list = all[videoId]?.toMutableList() ?: return
        if (index in list.indices) list.removeAt(index)
        all[videoId] = list
        File(context.filesDir, FILE_NAME).writeText(gson.toJson(all))
    }
}
