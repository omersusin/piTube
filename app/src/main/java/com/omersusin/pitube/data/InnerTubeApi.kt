package com.omersusin.pitube.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object InnerTubeApi {
    fun getStreamUrl(videoId: String): String? {
        return try {
            val client = OkHttpClient()
            val json = """
                {
                  "videoId": "$videoId",
                  "context": {
                    "client": {
                      "clientName": "ANDROID",
                      "clientVersion": "19.09.37",
                      "androidSdkVersion": 30
                    }
                  }
                }
            """.trimIndent()
            
            val body = json.toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-" + "df2KTyQ_vz_yYM39w")
                .addHeader("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip")
                .post(body)
                .build()
                
            val resp = client.newCall(req).execute()
            val respJson = JSONObject(resp.body?.string() ?: "{}")
            respJson.getJSONObject("streamingData").getJSONArray("formats")
                .getJSONObject(0).getString("url")
        } catch (e: Exception) { null }
    }
}
