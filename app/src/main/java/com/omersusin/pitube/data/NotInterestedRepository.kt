package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class NotInterestedEntry(
    val videoId: String?,
    val channelId: String?,
    val channelName: String?,
    val timestamp: Long = System.currentTimeMillis()
)

object NotInterestedRepository {
    private const val FILE_NAME = "not_interested.json"
    private const val SUPPRESSION_DAYS_VIDEO = 30L
    private const val SUPPRESSION_DAYS_CHANNEL = 14L
    private const val MAX_VIDEOS = 500
    private const val MAX_CHANNELS = 100
    private val gson = Gson()

    private fun getFile(context: Context) = File(context.filesDir, FILE_NAME)

    fun getAll(context: Context): List<NotInterestedEntry> {
        val file = getFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<NotInterestedEntry>>() {}.type
            gson.fromJson(file.readText(), type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun isVideoNotInterested(context: Context, videoId: String): Boolean {
        val cutoff = System.currentTimeMillis() - (SUPPRESSION_DAYS_VIDEO * 86_400_000L)
        return getAll(context).any { it.videoId == videoId && it.timestamp > cutoff }
    }

    fun isChannelNotInterested(context: Context, channelId: String): Boolean {
        val cutoff = System.currentTimeMillis() - (SUPPRESSION_DAYS_CHANNEL * 86_400_000L)
        return getAll(context).any { it.channelId == channelId && it.timestamp > cutoff }
    }

    fun markVideoNotInterested(context: Context, videoId: String, channelId: String?, channelName: String?) {
        val current = getAll(context).toMutableList()
        current.removeAll { it.videoId == videoId }
        current.add(0, NotInterestedEntry(videoId = videoId, channelId = channelId, channelName = channelName))
        if (current.size > MAX_VIDEOS) {
            val cutoff = System.currentTimeMillis() - (SUPPRESSION_DAYS_VIDEO * 86_400_000L)
            val pruned = current.filter { it.timestamp > cutoff }
            if (pruned.size > MAX_VIDEOS) {
                current = pruned.take(MAX_VIDEOS).toMutableList()
            } else {
                current = pruned.toMutableList()
            }
        }
        save(context, current)
    }

    fun markChannelNotInterested(context: Context, channelId: String, channelName: String?) {
        val current = getAll(context).toMutableList()
        current.removeAll { it.channelId == channelId }
        current.add(0, NotInterestedEntry(videoId = null, channelId = channelId, channelName = channelName))
        if (current.count { it.channelId != null } > MAX_CHANNELS) {
            val cutoff = System.currentTimeMillis() - (SUPPRESSION_DAYS_CHANNEL * 86_400_000L)
            val pruned = current.filter { it.timestamp > cutoff || it.videoId != null }
            current = pruned.toMutableList()
        }
        save(context, current)
    }

    fun undo(context: Context, videoId: String?) {
        val current = getAll(context).toMutableList()
        current.removeAll { it.videoId == videoId }
        save(context, current)
    }

    fun undoChannel(context: Context, channelId: String) {
        val current = getAll(context).toMutableList()
        current.removeAll { it.channelId == channelId }
        save(context, current)
    }

    fun clear(context: Context) {
        getFile(context).delete()
    }

    private fun save(context: Context, entries: List<NotInterestedEntry>) {
        getFile(context).writeText(gson.toJson(entries))
    }
}
