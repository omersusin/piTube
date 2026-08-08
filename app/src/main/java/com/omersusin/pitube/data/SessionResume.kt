package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import java.io.File

data class ResumeData(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String?,
    val channelName: String,
    val positionMs: Long,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

object SessionResume {
    private const val FILE_NAME = "session_resume.json"
    private val gson = Gson()

    private fun getFile(context: Context) = File(context.filesDir, FILE_NAME)

    fun save(context: Context, video: VideoItem, positionMs: Long) {
        try {
            val data = ResumeData(
                videoId = video.videoId,
                title = video.title,
                thumbnailUrl = video.thumbnailUrl,
                channelName = video.uploaderName,
                positionMs = positionMs,
                durationMs = (video.duration * 1000).toLong()
            )
            getFile(context).writeText(gson.toJson(data))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun load(context: Context): ResumeData? {
        val file = getFile(context)
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), ResumeData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        getFile(context).delete()
    }
}
