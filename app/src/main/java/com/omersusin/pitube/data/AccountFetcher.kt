package com.omersusin.pitube.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object AccountFetcher {
    private val client = OkHttpClient()
    private const val PREFS = "pitube_account"

    data class AccountInfo(val name: String, val avatarUrl: String?, val handle: String = "")

    fun getCached(context: Context): AccountInfo? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = p.getString("name", null) ?: return null
        return AccountInfo(name, p.getString("url", null), p.getString("handle", "") ?: "")
    }

    fun cache(context: Context, info: AccountInfo) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name", info.name).putString("url", info.avatarUrl).putString("handle", info.handle).apply()
    }

    fun clearCache(context: Context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply() }

    suspend fun fetch(context: Context): AccountInfo? = withContext(Dispatchers.IO) {
        InnerTubeFeed.fetchAccount(context) ?: regexFetch(context)
    }

    private fun regexFetch(context: Context): AccountInfo? {
        return try {
            val raw = AuthManager.getRawCookies(context)
            if (raw.isBlank()) return null
            val req = Request.Builder().url("https://www.youtube.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Cookie", raw).header("Accept-Language", "en-US,en;q=0.9").build()
            val html = client.newCall(req).execute().body?.string() ?: return null
            val photo = Regex("\"topbarAvatarViewModel\":\\{[^}]*?\"photoUrl\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                ?: Regex("\"photoUrl\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            val name = Regex("\"accountName\":\\{\"simpleText\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                ?: Regex("\"accountName\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            if (photo == null && name == null) null
            else AccountInfo(name ?: "Account", photo?.replace("\\/", "/")?.let { if (it.startsWith("//")) "https:$it" else it })
        } catch (e: Exception) { null }
    }
}
