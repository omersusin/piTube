package com.omersusin.pitube.data
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaType

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AccountFetcher {
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
            val cookies = AuthManager.getCookies(context)
            if (cookies.isEmpty()) return@withContext null
            val client = okhttp3.OkHttpClient()
            val body = """{"context":{"client":{"clientName":"WEB","clientVersion":"2.20240101.00.00"}}}"""
            val req = okhttp3.Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/account/account_menu?key=AIzaSyAO_FJ2SlqU8Q4DEHLAQ9D_042zB78vy3cA")
                .post(okhttp3.RequestBody.Companion.create(okhttp3.MediaType.parse("application/json"), body))
                .addHeader("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null
            val json = org.json.JSONObject(resp.body?.string() ?: return@withContext null)
            val actions = json.optJSONArray("actions") ?: return@withContext null
            val item = actions.optJSONObject(0)?.optJSONObject("openPopupAction")?.optJSONObject("popup")?.optJSONObject("multiPageMenuRenderer")?.optJSONArray("sections")?.optJSONObject(0)?.optJSONObject("accountSectionListRenderer")?.optJSONArray("contents")?.optJSONObject(0)?.optJSONObject("accountItem") ?: return@withContext null
            val name = item.optString("accountName", "Unknown")
            val photo = item.optString("accountPhoto", "")
            val info = AccountInfo(name, photo, null)
            cache(context, info)
            info
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
