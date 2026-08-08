package com.omersusin.pitube.data

import android.content.Context
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object AccountFetcher {
    private const val INNERTUBE_API_URL = "https://www.youtube.com/youtubei/v1/account/account_menu"
    private const val INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4DEHLAQ9D_042zB78vy3cA"

    data class AccountInfo(
        val name: String,
        val avatarUrl: String?,
        val handle: String? = null
    )

    fun fetch(context: Context): AccountInfo? {
        val client = OkHttpClient()
        val cookies = AuthManager.getCookies(context)
        
        if (cookies.isEmpty()) return null

        val cookieString = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

        val requestBody = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB",
                        "clientVersion": "2.20240101.00.00"
                    }
                }
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("$INNERTUBE_API_URL?key=$INNERTUBE_API_KEY")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .addHeader("Cookie", cookieString)
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            
            val json = JSONObject(response.body?.string() ?: return null)
            val actions = json.optJSONArray("actions") ?: return null
            val accountItem = actions.optJSONObject(0)?.optJSONObject("openPopupAction")?.optJSONObject("popup")?.optJSONObject("multiPageMenuRenderer")?.optJSONArray("sections")?.optJSONObject(0)?.optJSONObject("accountSectionListRenderer")?.optJSONArray("contents")?.optJSONObject(0)?.optJSONObject("accountItem") ?: return null
            
            val accountName = accountItem.optString("accountName", "Unknown")
            val accountPhoto = accountItem.optString("accountPhoto", "")
            
            val info = AccountInfo(accountName, accountPhoto, null)
            cache(context, info)
            info
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    fun getCached(context: Context): AccountInfo? {
        val prefs = context.getSharedPreferences("account_cache", Context.MODE_PRIVATE)
        val name = prefs.getString("name", null) ?: return null
        val photo = prefs.getString("photo", "")
        val handle = prefs.getString("handle", null)
        return AccountInfo(name, photo, handle)
    }

    fun cache(context: Context, info: AccountInfo) {
        val prefs = context.getSharedPreferences("account_cache", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("name", info.name)
            .putString("photo", info.avatarUrl ?: "")
            .putString("handle", info.handle ?: "")
            .apply()
    }
}
