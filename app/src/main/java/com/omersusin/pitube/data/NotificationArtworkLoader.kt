package com.omersusin.pitube.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

object NotificationArtworkLoader {
    private const val ICON_SIZE_PX = 256
    private const val MAX_ENTRIES = 16

    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, Bitmap>(MAX_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>) = size > MAX_ENTRIES
        }
    )

    fun cached(url: String?): Bitmap? = url?.let { cache[it] }

    suspend fun load(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        cache[url]?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(ICON_SIZE_PX, ICON_SIZE_PX)
                    .allowHardware(false)
                    .build()
                val drawable = context.imageLoader.execute(request).drawable ?: return@withContext null
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                    ?: drawable.toBitmap(ICON_SIZE_PX, ICON_SIZE_PX)
                cache[url] = bitmap
                bitmap
            } catch (e: Exception) { null }
        }
    }
}
