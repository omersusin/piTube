package com.omersusin.pitube.translation

import com.omersusin.pitube.network.AppProxyManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * One shared HttpClient for every translation engine. All engines post to
 * absolute URLs per request, so a single stateless client (with the app's
 * proxy applied) is all we need - unlike the upstream Retrofit port which
 * kept one client per engine.
 *
 * Deliberately mirrors the InnerTube client's Json config
 * (ignoreUnknownKeys + encodeDefaults), applies [AppProxyManager] so a proxy
 * config translates non-YouTube traffic too, and uses a long read timeout
 * because LLM endpoints can take tens of seconds for long texts.
 */
object TranslationHttpClient {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
            engine {
                config {
                    connectTimeout(30, TimeUnit.SECONDS)
                    readTimeout(120, TimeUnit.SECONDS)
                    writeTimeout(30, TimeUnit.SECONDS)
                    followRedirects(true)
                    retryOnConnectionFailure(true)
                    AppProxyManager.applyTo(this)
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 120_000
            }
        }
    }
}