package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class VideoNote(
    val timeMs: Long,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

object NotesManager {
    private const val FILE_PREFIX = "notes_"
    private val gson = Gson()

    private fun getFile(context: Context, videoId: String) = File(context.filesDir, "$FILE_PREFIX$videoId.json")

    fun getNotes(context: Context, videoId: String): List<VideoNote> {
        val file = getFile(context, videoId)
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<VideoNote>>() {}.type
            gson.fromJson(file.readText(), type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun addNote(context: Context, videoId: String, note: VideoNote) {
        val notes = getNotes(context, videoId).toMutableList()
        notes.add(note)
        notes.sortBy { it.timeMs }
        saveNotes(context, videoId, notes)
    }

    fun updateNote(context: Context, videoId: String, index: Int, note: VideoNote) {
        val notes = getNotes(context, videoId).toMutableList()
        if (index in notes.indices) {
            notes[index] = note
            notes.sortBy { it.timeMs }
            saveNotes(context, videoId, notes)
        }
    }

    fun deleteNote(context: Context, videoId: String, index: Int) {
        val notes = getNotes(context, videoId).toMutableList()
        if (index in notes.indices) {
            notes.removeAt(index)
            saveNotes(context, videoId, notes)
        }
    }

    fun clearNotes(context: Context, videoId: String) {
        getFile(context, videoId).delete()
    }

    private fun saveNotes(context: Context, videoId: String, notes: List<VideoNote>) {
        getFile(context, videoId).writeText(gson.toJson(notes))
    }
}
