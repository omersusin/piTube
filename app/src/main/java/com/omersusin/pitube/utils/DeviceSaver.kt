package com.omersusin.pitube.utils

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Copies a completed download out of app-private storage into the device's
 * public media collections (Gallery / music apps), the way Seal and YTDLnis
 * expose "export to library".
 *
 * - API 29+: MediaStore insert into Movies/piTube or Music/piTube.
 * - API 26-28: direct write to the public directory + media-scanner ping
 *   (pre-scoped-storage devices grant WRITE_EXTERNAL_STORAGE at install).
 */
object DeviceSaver {

    fun saveToDevice(
        context: Context,
        sourceFile: File,
        title: String,
        isAudio: Boolean,
    ): Result<Uri> = runCatching {
        require(sourceFile.exists() && sourceFile.length() > 0) { "source file missing" }
        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "pitube_export" }
        val extension = sourceFile.extension.ifBlank { if (isAudio) "m4a" else "mp4" }
        val mime = when (extension.lowercase()) {
            "m4a", "mp4a", "mp3" -> if (isAudio) {
                if (extension.lowercase() == "mp3") "audio/mpeg" else "audio/mp4"
            } else "video/mp4"
            "opus" -> if (isAudio) "audio/opus" else "video/webm"
            "webm" -> if (isAudio) "audio/webm" else "video/webm"
            "mkv" -> "video/x-matroska"
            else -> if (isAudio) "audio/mp4" else "video/mp4"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection =
                if (isAudio) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val relativeDir = (if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) + "/piTube"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$safeTitle.$extension")
                put(MediaStore.MediaColumns.MIME_TYPE, finalMime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: error("MediaStore insert failed")
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { it.copyTo(out) }
            } ?: error("output stream null")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            @Suppress("DEPRECATION")
            val publicDir = Environment.getExternalStoragePublicDirectory(
                if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES,
            )
            val targetDir = File(publicDir, "piTube").apply { mkdirs() }
            val target = File(targetDir, "$safeTitle.$extension")
            sourceFile.copyTo(target, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(finalMime), null)
            Uri.fromFile(target)
        }
    }

    private fun mimeForVideo(extension: String): String = when (extension.lowercase()) {
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        else -> "video/mp4"
    }
}
