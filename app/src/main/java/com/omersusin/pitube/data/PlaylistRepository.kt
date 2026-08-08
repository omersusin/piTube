package com.omersusin.pitube.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.random.Random

data class UserPlaylist(
    val id: String,
    val name: String,
    val description: String? = null,
    val coverPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val videoIds: List<String> = emptyList()
)

object PlaylistRepository {
    private val gson = Gson()
    private fun getPlaylistDir(context: Context) = File(context.filesDir, "playlists").apply { mkdirs() }
    private fun getCoversDir(context: Context) = File(context.filesDir, "playlist_covers").apply { mkdirs() }

    fun getAll(context: Context): List<UserPlaylist> {
        val dir = getPlaylistDir(context)
        val files = dir.listFiles { _, n -> n.endsWith(".json") } ?: return emptyList()
        val type = object : TypeToken<UserPlaylist>() {}.type
        return files.mapNotNull { f ->
            try { gson.fromJson<UserPlaylist>(f.readText(), type) } catch (e: Exception) { null }
        }.sortedByDescending { it.createdAt }
    }

    fun get(context: Context, id: String): UserPlaylist? =
        getAll(context).find { it.id == id }

    suspend fun create(context: Context, name: String, description: String?): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val coverPath = generateCover(context, name, id)
        val playlist = UserPlaylist(id = id, name = name, description = description, coverPath = coverPath)
        save(context, playlist)
        id
    }

    suspend fun addVideo(context: Context, playlistId: String, videoId: String) = withContext(Dispatchers.IO) {
        val p = get(context, playlistId) ?: return@withContext
        if (videoId in p.videoIds) return@withContext
        save(context, p.copy(videoIds = p.videoIds + videoId))
    }

    suspend fun removeVideo(context: Context, playlistId: String, videoId: String) = withContext(Dispatchers.IO) {
        val p = get(context, playlistId) ?: return@withContext
        save(context, p.copy(videoIds = p.videoIds - videoId))
    }

    suspend fun delete(context: Context, playlistId: String) = withContext(Dispatchers.IO) {
        File(getPlaylistDir(context), "$playlistId.json").delete()
        File(getCoversDir(context), "cover_$playlistId.png").delete()
    }

    suspend fun rename(context: Context, playlistId: String, name: String) = withContext(Dispatchers.IO) {
        val p = get(context, playlistId) ?: return@withContext
        save(context, p.copy(name = name.trim()))
    }

    private fun save(context: Context, playlist: UserPlaylist) {
        File(getPlaylistDir(context), "${playlist.id}.json").writeText(gson.toJson(playlist))
    }

    private fun generateCover(context: Context, name: String, id: String): String {
        val size = 1000
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val hue1 = Random.nextFloat() * 360f
        val hue2 = (hue1 + 40 + Random.nextFloat() * 100) % 360
        val color1 = Color.HSVToColor(floatArrayOf(hue1, 0.8f, 0.9f))
        val color2 = Color.HSVToColor(floatArrayOf(hue2, 0.8f, 0.8f))
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), color1, color2, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        val letter = name.take(1).uppercase()
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = size * 0.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            setShadowLayer(20f, 0f, 10f, Color.argb(100, 0, 0, 0))
        }
        canvas.drawText(letter, size / 2f, (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2), textPaint)
        val file = File(getCoversDir(context), "cover_$id.png")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return file.absolutePath
    }
}
