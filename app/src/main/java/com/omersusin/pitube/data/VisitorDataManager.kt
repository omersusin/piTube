package com.omersusin.pitube.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object VisitorDataManager {
    private const val TAG = "VisitorData"
    private const val TTL_MS = 6 * 60 * 60 * 1000L
    private const val WEB_VERSION = "2.20260114.08.00"
    private val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val mutex = Mutex()
    @Volatile private var cached: String? = null
    @Volatile private var fetchedAt: Long = 0L
    private var prefs: SharedPreferences? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun init(context: Context) {
        if (prefs == null) prefs = context.getSharedPreferences("pitube_visitor", Context.MODE_PRIVATE)
    }

    private fun persisted(): String? {
        val p = prefs ?: return null
        val v = p.getString("visitor_data", null)?.takeIf { it.isNotBlank() } ?: return null
        val at = p.getLong("visitor_data_at", 0L)
        if (System.currentTimeMillis() - at < TTL_MS) {
            fetchedAt = at
            return v
        }
        return null
    }

    private fun persist(value: String) {
        val p = prefs ?: return
        p.edit().putString("visitor_data", value).putLong("visitor_data_at", System.currentTimeMillis()).apply()
    }

    suspend fun get(): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            cached?.takeIf { now - fetchedAt < TTL_MS }?.let { return@withLock it }
            persisted()?.let {
                cached = it
                return@withLock it
            }
            val fresh = fetchFromApi() ?: fetchFromBootstrap()
            if (fresh != null) {
                cached = fresh
                fetchedAt = System.currentTimeMillis()
                persist(fresh)
            }
            fresh ?: cached.orEmpty()
        }
    }

    suspend fun remint(flagged: String? = null): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (flagged != null && cached == flagged) {
                cached = null
                prefs?.edit()?.remove("visitor_data")?.apply()
            }
            val fresh = fetchFromApi() ?: fetchFromBootstrap()
            if (fresh != null) {
                cached = fresh
                fetchedAt = System.currentTimeMillis()
                persist(fresh)
            }
            fresh
        }
    }

    private fun fetchFromApi(): String? {
        return try {
            val body = JSONObject().put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", WEB_VERSION)
                        put("hl", "en")
                        put("gl", "US")
                    }
                )
            ).toString()
            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false")
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("User-Agent", BROWSER_UA)
                .build()
            client.newCall(request).execute().use { resp ->
                val json = resp.body?.string().orEmpty()
                JSONObject(json)
                    .optJSONObject("responseContext")
                    ?.optString("visitorData")
                    ?.replace("%3D", "=")
                    ?.replace("%3d", "=")
                    ?.takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "visitor_id mint failed: ${e.message}")
            null
        }
    }

    private fun fetchFromBootstrap(): String? {
        return try {
            val request = Request.Builder()
                .url("https://www.youtube.com/")
                .addHeader("User-Agent", BROWSER_UA)
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .build()
            client.newCall(request).execute().use { resp ->
                val html = resp.body?.string().orEmpty()
                Regex("\"visitorData\":\"(.*?)\"").find(html)
                    ?.groupValues?.get(1)
                    ?.replace("\\u003d", "=")
                    ?.replace("\\u0026", "&")
                    ?.takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "bootstrap visitorData scrape failed: ${e.message}")
            null
        }
    }
}
