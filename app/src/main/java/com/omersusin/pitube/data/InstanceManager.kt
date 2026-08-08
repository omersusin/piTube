package com.omersusin.pitube.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class PipedInstanceInfo(val api_url: String?, val up: Boolean = false)

object InstanceManager {
    private val defaults = listOf(
        "pipedapi.adminforge.de",
        "api.piped.private.coffee",
        "pipedapi.r4fo.com",
        "pipedapi.leptons.xyz",
        "piped-api.lunar.icu",
        "pipedapi.kavin.rocks"
    )
    @Volatile var instances: List<String> = defaults
    @Volatile var current: String = defaults[0]

    val gson: Gson = GsonBuilder().setLenient().create()

    init {
        Thread { refreshInstances() }.start()
    }

    fun refreshInstances() {
        try {
            val client = OkHttpClient()
            val req = Request.Builder().url("https://piped-instances.kavin.rocks/").build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return
            val type = object : TypeToken<List<PipedInstanceInfo>>() {}.type
            val list: List<PipedInstanceInfo> = gson.fromJson(body, type) ?: return
            val hosts = list.filter { it.up }.mapNotNull { it.api_url?.removePrefix("https://")?.removePrefix("http://") }.distinct()
            if (hosts.isNotEmpty()) { instances = hosts; current = hosts[0] }
        } catch (e: Exception) { }
    }
}

class PipedFailoverInterceptor : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val hosts = (listOf(InstanceManager.current) + InstanceManager.instances.filter { it != InstanceManager.current }).distinct()
        var lastError: IOException? = null
        val original = chain.request()
        for (host in hosts) {
            try {
                val newUrl = original.url.newBuilder().scheme("https").host(host).build()
                val res = chain.proceed(original.newBuilder().url(newUrl).build())
                // Check that response is actually JSON, not HTML error page
                val contentType = res.header("Content-Type") ?: ""
                if (res.isSuccessful && contentType.contains("application/json")) {
                    InstanceManager.current = host
                    return res
                }
                if (res.code == 400 || res.code == 404) return res
                res.close()
            } catch (e: IOException) { lastError = e }
        }
        throw lastError ?: IOException("All Piped instances failed")
    }
}
