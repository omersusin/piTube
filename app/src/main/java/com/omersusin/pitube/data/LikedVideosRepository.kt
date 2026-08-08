package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object LikedVideosRepository {
    private val gson = Gson()
    private fun file(context: Context) = File(context.filesDir, "liked_videos.json")

    fun getAll(context: Context): List<VideoItem> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            val t = object : TypeToken<List<VideoItem>>() {}.type
            gson.fromJson(f.readText(), t) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun isLiked(context: Context, videoId: String): Boolean = getAll(context).any { it.videoId == videoId }

    fun toggle(context: Context, video: VideoItem): Boolean {
        val list = getAll(context).toMutableList()
        return if (list.removeAll { it.videoId == video.videoId }) {
            file(context).writeText(gson.toJson(list)); false
        } else {
            list.add(0, video); file(context).writeText(gson.toJson(list)); true
        }
    }
}
