package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object LikedVideosRepository {
    private const val FILE_NAME = "liked_videos.json"
    private val gson = Gson()
    private fun getFile(context: Context) = File(context.filesDir, FILE_NAME)

    fun getAll(context: Context): List<VideoItem> {
        val file = getFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<VideoItem>>() {}.type
            gson.fromJson(file.readText(), type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun isLiked(context: Context, videoId: String): Boolean =
        getAll(context).any { it.videoId == videoId || it.url == videoId }

    fun toggle(context: Context, video: VideoItem): Boolean {
        val current = getAll(context).toMutableList()
        val id = video.videoId
        val existing = current.find { it.videoId == id || it.url == id }
        return if (existing != null) {
            current.remove(existing)
            save(context, current)
            false
        } else {
            current.add(0, video)
            save(context, current)
            true
        }
    }

    fun like(context: Context, video: VideoItem) {
        val current = getAll(context).toMutableList()
        if (current.any { it.videoId == video.videoId || it.url == video.videoId }) return
        current.add(0, video)
        save(context, current)
    }

    fun unlike(context: Context, videoId: String) {
        val current = getAll(context).toMutableList()
        current.removeAll { it.videoId == videoId || it.url == videoId }
        save(context, current)
    }

    fun clear(context: Context) {
        getFile(context).delete()
    }

    private fun save(context: Context, videos: List<VideoItem>) {
        getFile(context).writeText(gson.toJson(videos))
    }
}
