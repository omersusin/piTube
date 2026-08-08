package com.omersusin.pitube.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object AccountFetcher {
    private val client = OkHttpClient()
    private const val PREFS = "pitube_account"
    private const val KEY_NAME = "name"
    private const val KEY_URL = "url"

    data class AccountInfo(val name: String, val avatarUrl: String?)

    fun getCached(context: Context): AccountInfo? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = p.getString(KEY_NAME, null) ?: return null
        return AccountInfo(name, p.getString(KEY_URL, null))
    }

    fun cache(context: Context, info: AccountInfo) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_NAME, info.name).putString(KEY_URL, info.avatarUrl).apply()
    }

    fun clearCache(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    suspend fun fetch(context: Context): AccountInfo? = withContext(Dispatchers.IO) {
        try {
            val cookies = AuthManager.getCookies(context)
            if (cookies.isEmpty()) return@withContext null
            val cookieHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            val req = Request.Builder()
                .url("https://www.youtube.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Cookie", cookieHeader)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val html = client.newCall(req).execute().body?.string() ?: return@withContext null

            val photo = Regex("\"topbarAvatarViewModel\":\\{[^}]*?\"photoUrl\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                ?: Regex("\"photoUrl\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            val name = Regex("\"accountName\":\\{\"simpleText\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                ?: Regex("\"accountName\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)

            if (photo == null && name == null) null
            else AccountInfo(
                name ?: "Account",
                photo?.replace("\\/", "/")?.let { if (it.startsWith("//")) "https:$it" else it }
            )
        } catch (e: Exception) { e.printStackTrace(); null }
    }
}
