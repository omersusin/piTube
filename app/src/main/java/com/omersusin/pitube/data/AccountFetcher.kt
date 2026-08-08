package com.omersusin.pitube.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AccountFetcher {
    private const val TAG = "AccountFetcher"
    private const val API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val CLIENT_VERSION = "2.20260114.08.00"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    data class AccountInfo(
        val name: String,
        val avatarUrl: String?,
        val handle: String?,
        val channelId: String? = null
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun getCached(context: Context): AccountInfo? {
        val prefs = context.getSharedPreferences("account_cache", Context.MODE_PRIVATE)
        val name = prefs.getString("name", null) ?: return null
        val photo = prefs.getString("photo", null)
        val handle = prefs.getString("handle", null)
        val channelId = prefs.getString("channelId", null)
        return AccountInfo(name, photo, handle, channelId)
    }

    fun cache(context: Context, info: AccountInfo?) {
        if (info == null) { clearCache(context); return }
        val prefs = context.getSharedPreferences("account_cache", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("name", info.name)
            .putString("photo", info.avatarUrl ?: "")
            .putString("handle", info.handle ?: "")
            .putString("channelId", info.channelId ?: "")
            .apply()
    }

    fun clearCache(context: Context) {
        context.getSharedPreferences("account_cache", Context.MODE_PRIVATE).edit().clear().apply()
    }

    suspend fun fetch(context: Context): AccountInfo? = withContext(Dispatchers.IO) {
        try {
            val rawCookies = AuthManager.getRawCookies(context)
            if (rawCookies.isBlank()) return@withContext null

            val authHeader = KodaAuth.authHeader(rawCookies)

            val body = """{"context":{"client":{"clientName":"WEB","clientVersion":"$CLIENT_VERSION","hl":"en","gl":"US"}}}"""
            val reqBuilder = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/account/account_menu?key=$API_KEY")
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Cookie", rawCookies)
                .addHeader("User-Agent", UA)
                .addHeader("X-YouTube-Client-Name", "1")
                .addHeader("X-YouTube-Client-Version", CLIENT_VERSION)
                .addHeader("Origin", "https://www.youtube.com")
                .addHeader("X-Goog-AuthUser", "0")
            if (authHeader != null) reqBuilder.addHeader("Authorization", authHeader)

            val resp = client.newCall(reqBuilder.build()).execute()
            resp.use { r ->
                KodaAuth.refreshFromResponse(context, r)
                if (!r.isSuccessful) {
                    Log.w(TAG, "HTTP ${r.code}")
                    return@withContext null
                }
                val bodyStr = r.body?.string() ?: return@withContext null
                Log.d(TAG, "Response length: ${bodyStr.length}")
                val json = JSONObject(bodyStr)

                var name: String? = null
                var photo: String? = null
                var handle: String? = null
                var channelId: String? = null

                // Try primary path: actions[0].openPopupAction.popup.multiPageMenuRenderer
                val actions = json.optJSONArray("actions")
                if (actions != null) {
                    val item = actions.optJSONObject(0)
                        ?.optJSONObject("openPopupAction")
                        ?.optJSONObject("popup")
                        ?.optJSONObject("multiPageMenuRenderer")
                        ?.optJSONArray("sections")
                        ?.optJSONObject(0)
                        ?.optJSONObject("accountSectionListRenderer")
                        ?.optJSONArray("contents")
                        ?.optJSONObject(0)
                        ?.optJSONObject("accountItem")
                    if (item != null) {
                        name = item.optString("accountName", null)
                        photo = item.optString("accountPhoto", null)
                        handle = item.optString("accountHandle", null)
                        if (handle.isNullOrBlank()) handle = item.optString("channelHandle", null)
                        channelId = item.optString("channelId", null)
                        if (channelId.isNullOrBlank()) {
                            val navEndpoint = item.optJSONObject("navigationEndpoint")
                            channelId = navEndpoint?.optJSONObject("browseEndpoint")?.optString("browseId", null)
                        }
                    }
                }

                // Fallback: try header path
                if (name == null) {
                    val header = json.optJSONObject("header")
                        ?.optJSONObject("accountSectionListRenderer")
                        ?.optJSONArray("contents")
                        ?.optJSONObject(0)
                        ?.optJSONObject("accountItem")
                    if (header != null) {
                        name = header.optString("accountName", null)
                        photo = header.optString("accountPhoto", null)
                        handle = header.optString("accountHandle", null)
                        if (handle.isNullOrBlank()) handle = header.optString("channelHandle", null)
                        channelId = header.optString("channelId", null)
                    }
                }

                // Fallback: flat fields
                if (name == null) {
                    name = json.optString("name", null)
                    photo = json.optString("photoUrl", null)
                }

                // Fallback: scan for handle in profile
                if (handle.isNullOrBlank()) {
                    handle = findHandleInJson(json)
                }

                if (name.isNullOrBlank()) {
                    Log.w(TAG, "No account name found in response")
                    return@withContext null
                }

                Log.d(TAG, "Fetched account: $name, handle=$handle, channelId=$channelId")
                val info = AccountInfo(name, photo, handle, channelId)
                cache(context, info)
                info
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch account: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun findHandleInJson(json: JSONObject): String? {
        // Search common locations for handle
        val paths = listOf(
            "header" to "accountChannelHandle",
            "header" to "handle",
        )
        for ((parent, field) in paths) {
            val value = json.optJSONObject(parent)?.optString(field, null)
            if (!value.isNullOrBlank()) return value
        }
        // Search in accountSectionListRenderer
        val sections = json.optJSONObject("header")
            ?.optJSONObject("accountSectionListRenderer")
            ?.optJSONArray("contents")
        if (sections != null) {
            for (i in 0 until sections.length()) {
                val section = sections.optJSONObject(i)
                val accountItem = section?.optJSONObject("accountItem")
                if (accountItem != null) {
                    val h = accountItem.optString("accountHandle", null)
                    if (!h.isNullOrBlank()) return h
                    val ch = accountItem.optString("channelHandle", null)
                    if (!ch.isNullOrBlank()) return ch
                }
            }
        }
        return null
    }
}
