package com.omersusin.pitube.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

object DownloadManager {
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()
    private const val CHANNEL_ID = "pitube_downloads"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW))
            }
        }
    }

    private fun notify(context: Context, title: String, text: String, progress: Int, done: Boolean) {
        try {
            ensureChannel(context)
            val b = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("piTube: $title").setContentText(text).setOnlyAlertOnce(true)
            if (!done) b.setProgress(100, progress, false) else b.setProgress(0, 0, false)
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(9001, b.build())
        } catch (e: Exception) { }
    }

    private fun downloadToFile(url: String, file: File, onProgress: (Int) -> Unit): Boolean {
        return try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) return false
            val body = response.body ?: return false
            val total = body.contentLength()
            var downloaded = 0L
            file.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(8192)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read); downloaded += read
                        if (total > 0) onProgress(((downloaded * 100) / total).toInt())
                    }
                }
            }
            true
        } catch (e: Exception) { false }
    }

    private fun mux(videoFile: File, audioFile: File, outputFile: File) {
        val videoExtractor = MediaExtractor().apply { setDataSource(videoFile.path) }
        val audioExtractor = MediaExtractor().apply { setDataSource(audioFile.path) }
        val muxer = MediaMuxer(outputFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            videoExtractor.selectTrack(0)
            val videoTrack = muxer.addTrack(videoExtractor.getTrackFormat(0))
            audioExtractor.selectTrack(0)
            val audioTrack = muxer.addTrack(audioExtractor.getTrackFormat(0))
            muxer.start()
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(videoTrack, buffer, bufferInfo)
                videoExtractor.advance()
            }
            while (true) {
                bufferInfo.size = audioExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break
                bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                bufferInfo.flags = audioExtractor.sampleFlags
                muxer.writeSampleData(audioTrack, buffer, bufferInfo)
                audioExtractor.advance()
            }
            muxer.stop()
        } finally {
            try { muxer.release() } catch (_: Exception) {}
            try { videoExtractor.release() } catch (_: Exception) {}
            try { audioExtractor.release() } catch (_: Exception) {}
        }
    }

    private fun saveToMediaStore(context: Context, file: File, name: String) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/piTube")
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                }
            }
        }
        file.delete()
    }

    fun downloadVideo(context: Context, title: String, videoOnlyUrl: String?, audioUrl: String?, progressiveUrl: String?, item: DownloadTracker.DownloadItem) {
        Thread {
            try {
                val cacheDir = File(context.cacheDir, "download_temp").apply { mkdirs() }
                val finalName = "${title.take(60).replace(Regex("[^A-Za-z0-9 _-]"), "")}.mp4"
                var saved = false
                if (videoOnlyUrl != null && audioUrl != null && videoOnlyUrl != progressiveUrl) {
                    val vFile = File(cacheDir, "v.mp4"); val aFile = File(cacheDir, "a.mp4")
                    if (downloadToFile(videoOnlyUrl, vFile) { p -> item.progress = p / 2; notify(context, title, "Downloading video...", item.progress, false) } &&
                        downloadToFile(audioUrl, aFile) { p -> item.progress = 50 + p / 2; notify(context, title, "Downloading audio...", item.progress, false) }) {
                        val out = File(cacheDir, finalName)
                        try {
                            notify(context, title, "Merging...", 100, false)
                            mux(vFile, aFile, out)
                            saveToMediaStore(context, out, finalName)
                            saved = true
                        } catch (e: Exception) { }
                    }
                    vFile.delete(); aFile.delete()
                }
                if (!saved) {
                    val url = progressiveUrl ?: videoOnlyUrl ?: throw Exception("No URL")
                    val f = File(cacheDir, finalName)
                    if (!downloadToFile(url, f) { p -> item.progress = p; notify(context, title, "Downloading...", p, false) }) throw Exception("Download failed")
                    saveToMediaStore(context, f, finalName)
                }
                item.progress = 100; item.status = "done"
                notify(context, title, "Download complete ✓", 100, true)
            } catch (e: Exception) {
                item.status = "failed"
                notify(context, title, "Download failed", 0, true)
            }
        }.start()
    }
}
