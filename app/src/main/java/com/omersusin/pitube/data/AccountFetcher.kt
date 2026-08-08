package com.omersusin.pitube.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AccountFetcher {
    private const val TAG = "AccountFetcher"
    data class AccountInfo(val name: String, val avatarUrl: String?, val handle: String?)

    fun getCached(context: Context): AccountInfo? {
        val prefs = context.getSharedPreferences("account_cache", Context.MODE_PRIVATE)
        val name = prefs.getString("name", null) ?: return null
        val photo = prefs.getString("photo", null)
        val handle = prefs.getString("handle", null)
        return AccountInfo(name, photo, handle)
    }

    fun cache(context: Context, info: AccountInfo?) {
        if (info == null) { clearCache(context); return }
        val prefs = context.getSharedPreferences("account_cache", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("name", info.name)
            .putString("photo", info.avatarUrl ?: "")
            .putString("handle", info.handle ?: "")
            .apply()
    }

    fun clearCache(context: Context) {
        context.getSharedPreferences("account_cache", Context.MODE_PRIVATE).edit().clear().apply()
    }

    suspend fun fetch(context: Context): AccountInfo? = withContext(Dispatchers.IO) {
        try {
            val rawCookies = AuthManager.getRawCookies(context)
            if (rawCookies.isBlank()) return@withContext null

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val authHeader = KodaAuth.authHeader(rawCookies)

            val body = """{"context":{"client":{"clientName":"WEB","clientVersion":"2.20250101.00.00","hl":"en","gl":"US"}}}"""
            val req = okhttp3.Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/account/account_menu?key=AIzaSyAO_FJ2SlqU8Q4DEHLAQ9D_042zB78vy3cA")
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Cookie", rawCookies)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .apply { if (authHeader != null) addHeader("Authorization", authHeader) }
                .build()
            val resp = client.newCall(req).execute()
            resp.use { r ->
                if (!r.isSuccessful) {
                    Log.w(TAG, "HTTP ${r.code}")
                    return@withContext null
                }
                val json = org.json.JSONObject(r.body?.string() ?: return@withContext null)

                // Try multiple response formats
                var name: String? = null
                var photo: String? = null

                // Format 1: actions -> openPopupAction -> multiPageMenuRenderer
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
                    }
                }

                // Format 2: header -> accountSectionListRenderer
                if (name == null) {
                    val header = json.optJSONObject("header")
                        ?.optJSONObject("accountSectionListRenderer")
                        ?.optJSONArray("contents")
                        ?.optJSONObject(0)
                        ?.optJSONObject("accountItem")
                    if (header != null) {
                        name = header.optString("accountName", null)
                        photo = header.optString("accountPhoto", null)
                    }
                }

                // Format 3: Direct profile info
                if (name == null) {
                    name = json.optString("name", null)
                    photo = json.optString("photoUrl", null)
                }

                if (name.isNullOrBlank()) {
                    Log.w(TAG, "No account name found in response")
                    return@withContext null
                }

                Log.d(TAG, "Fetched account: $name")
                val info = AccountInfo(name, photo, null)
                cache(context, info)
                info
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch account: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}
