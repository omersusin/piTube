package com.omersusin.pitube.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object InnerTubeApi {
    private const val KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val UA = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L) gzip"

    fun getStreamUrl(videoId: String): String? {
        return try {
            val client = OkHttpClient()
            val json = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_VR")
                        put("clientVersion", "1.65.10")
                        put("androidSdkVersion", 32)
                        put("deviceModel", "Quest 3")
                    })
                })
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val body = json.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?key=$KEY")
                .addHeader("User-Agent", UA)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            val resp = client.newCall(req).execute()
            val respJson = JSONObject(resp.body?.string() ?: "{}")
            respJson.getJSONObject("streamingData").getJSONArray("formats")
                .getJSONObject(0).getString("url")
        } catch (e: Exception) { null }
    }
}
