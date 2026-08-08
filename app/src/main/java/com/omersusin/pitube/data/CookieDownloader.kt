package com.omersusin.pitube.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

class CookieDownloader(private val cookies: Map<String, String>) : Downloader() {
    
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                if (cookies.isNotEmpty()) {
                    val cookieString = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                    requestBuilder.addHeader("Cookie", cookieString)
                }
                requestBuilder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                chain.proceed(requestBuilder.build())
            }
            .build()
    }

    override fun execute(request: Request): Response {
        val url = request.url()
        val httpMethod = request.httpMethod()
        val headers = request.headers()
        val dataToSend = request.dataToSend()
        
        var requestBody: RequestBody? = null
        if (dataToSend != null) {
            requestBody = RequestBody.create(null, dataToSend)
        }
        
        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            .method(httpMethod, requestBody)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        
        headers.forEach { (key, values) ->
            values.forEach { value ->
                requestBuilder.addHeader(key, value)
            }
        }
        
        val response = client.newCall(requestBuilder.build()).execute()
        
        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge required", url)
        }
        
        val responseBody = response.body?.string() ?: ""
        val responseHeaders = response.headers.names().associateWith { name ->
            response.headers.values(name)
        }
        
        return Response(
            response.code,
            response.message,
            responseHeaders,
            responseBody,
            url
        )
    }
    
    companion object {
        fun initWithCookies(context: Context) {
            val cookies = AuthManager.getCookies(context)
            org.schabi.newpipe.extractor.NewPipe.init(CookieDownloader(cookies))
        }
    }
}
