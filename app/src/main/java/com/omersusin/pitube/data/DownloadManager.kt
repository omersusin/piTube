package com.omersusin.pitube.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

object DownloadManager {
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()

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
                        out.write(buf, 0, read)
                        downloaded += read
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
        muxer.release()
        videoExtractor.release()
        audioExtractor.release()
    }

    fun downloadVideo(context: Context, title: String, videoUrl: String, audioUrl: String?, onProgress: (Int) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
        val cacheDir = File(context.cacheDir, "download_temp")
        cacheDir.mkdirs()
        val vFile = File(cacheDir, "video.mp4")
        val aFile = File(cacheDir, "audio.mp4")
        val finalName = "${title.take(60).replace(Regex("[^A-Za-z0-9 _-]"), "")}.mp4"

        if (!downloadToFile(videoUrl, vFile, { p -> onProgress(p / 2) })) { onError("Video failed"); return }
        
        if (audioUrl != null) {
            if (!downloadToFile(audioUrl, aFile, { p -> onProgress(50 + p / 2) })) { onError("Audio failed"); return }
            val finalFile = File(cacheDir, finalName)
            try { mux(vFile, aFile, finalFile) } catch (e: Exception) { onError("Mux failed: ${e.message}"); return }
            saveToMediaStore(context, finalFile, finalName, onDone, onError)
        } else {
            saveToMediaStore(context, vFile, finalName, onDone, onError)
        }
        
        vFile.delete()
        aFile.delete()
    }

    private fun saveToMediaStore(context: Context, file: File, name: String, onDone: () -> Unit, onError: (String) -> Unit) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/piTube")
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(file.readBytes()) }
            }
            onDone()
        } catch (e: Exception) { onError("Save failed") }
    }
}
