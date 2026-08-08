package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class SavedChannel(
    val channelId: String,
    val name: String,
    val avatarUrl: String
)

object SubscriptionManager {
    private const val FILE_NAME = "subscriptions.json"
    private val gson = Gson()

    fun getSavedChannels(context: Context): List<SavedChannel> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        
        val json = file.readText()
        val type = object : TypeToken<List<SavedChannel>>() {}.type
        return try { gson.fromJson(json, type) ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    fun saveChannel(context: Context, channel: SavedChannel) {
        val channels = getSavedChannels(context).toMutableList()
        if (channels.none { it.channelId == channel.channelId }) {
            channels.add(channel)
            File(context.filesDir, FILE_NAME).writeText(gson.toJson(channels))
        }
    }

    fun removeChannel(context: Context, channelId: String) {
        val channels = getSavedChannels(context).toMutableList()
        channels.removeAll { it.channelId == channelId }
        File(context.filesDir, FILE_NAME).writeText(gson.toJson(channels))
    }
}
