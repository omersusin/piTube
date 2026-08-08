package com.omersusin.pitube.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object NotInterested {
    val version = mutableIntStateOf(0)
    data class Entry(val videoId: String?, val channel: String?, val title: String)
    private val gson = Gson()

    private fun load(context: Context): MutableList<Entry> {
        val f = File(context.filesDir, "not_interested.json")
        if (!f.exists()) return mutableListOf()
        return try { gson.fromJson(f.readText(), object : TypeToken<MutableList<Entry>>() {}.type) ?: mutableListOf() } catch (e: Exception) { mutableListOf() }
    }
    private fun save(context: Context, list: List<Entry>) { File(context.filesDir, "not_interested.json").writeText(gson.toJson(list)); version.intValue++ }

    fun all(context: Context) = load(context)
    fun hideVideo(context: Context, v: VideoItem) { save(context, load(context) + Entry(v.videoId, null, v.title)) }
    fun hideChannel(context: Context, name: String) { save(context, load(context) + Entry(null, name, "Channel: $name")) }
    fun unhide(context: Context, e: Entry) { save(context, load(context).filterNot { it == e }) }
    fun isHidden(context: Context, videoId: String, channel: String): Boolean {
        val l = load(context)
        return l.any { (it.videoId != null && it.videoId == videoId) || (it.channel != null && it.channel == channel) }
    }
}
