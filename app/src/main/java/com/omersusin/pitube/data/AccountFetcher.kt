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

    fun fetchAccount(context: Context): AccountInfo? {
        val client = OkHttpClient()
        val cookies = AuthManager.getCookies(context)
        
        if (cookies.isEmpty()) return null

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
            .addHeader("Cookie", cookies)
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            
            val json = JSONObject(response.body?.string() ?: return null)
            val actions = json.optJSONArray("actions") ?: return null
            val accountItem = actions.optJSONObject(0)?.optJSONObject("openPopupAction")?.optJSONObject("popup")?.optJSONObject("multiPageMenuRenderer")?.optJSONArray("sections")?.optJSONObject(0)?.optJSONObject("accountSectionListRenderer")?.optJSONArray("contents")?.optJSONObject(0)?.optJSONObject("accountItem") ?: return null
            
            val accountName = accountItem.optString("accountName", "Unknown")
            val accountPhoto = accountItem.optString("accountPhoto", "")
            
            AccountInfo(accountName, accountPhoto)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    fun getCachedAccount(context: Context): AccountInfo? {
        val prefs = context.getSharedPreferences("account_cache", Context.MODE_PRIVATE)
        val name = prefs.getString("name", null) ?: return null
        val photo = prefs.getString("photo", "") ?: ""
        return AccountInfo(name, photo)
    }

    fun cacheAccount(context: Context, info: AccountInfo) {
        val prefs = context.getSharedPreferences("account_cache", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("name", info.name)
            .putString("photo", info.photoUrl)
            .apply()
    }

    data class AccountInfo(val name: String, val photoUrl: String)
}
