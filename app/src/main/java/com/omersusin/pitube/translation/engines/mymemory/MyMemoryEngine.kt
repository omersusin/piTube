package com.omersusin.pitube.translation.engines.mymemory

import com.omersusin.pitube.translation.ApiKeyState
import com.omersusin.pitube.translation.CommonLanguages
import com.omersusin.pitube.translation.EngineSettingsProvider
import com.omersusin.pitube.translation.Language
import com.omersusin.pitube.translation.Translation
import com.omersusin.pitube.translation.TranslationEngine
import com.omersusin.pitube.translation.TranslationHttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable

@Serializable
data class MMTranslationResponse(
    val responseData: MMResponseData? = null,
    val responseStatus: Int? = null,
    val responseDetails: String? = null,
)

@Serializable
data class MMResponseData(
    val translatedText: String? = null,
    val detectedLanguage: String? = null,
)

/**
 * MyMemory - free translation memory with an optional API key. Language list
 * is hardcoded upstream; we reuse the device-built common list instead.
 * Ported from Translate You's MMEngine (GPL-3.0).
 */
class MyMemoryEngine(settingsProvider: EngineSettingsProvider) : TranslationEngine(settingsProvider) {

    override val name: String = "MyMemory"

    override val defaultUrl: String = "https://api.mymemory.translated.net"

    override val urlModifiable: Boolean = false

    override val apiKeyState: ApiKeyState = ApiKeyState.OPTIONAL

    override val autoLanguageCode: String? = "Autodetect"

    override suspend fun getLanguages(): List<Language> = CommonLanguages.languages

    override suspend fun translate(query: String, source: String, target: String): Translation {
        val body = TranslationHttpClient.client.get(getUrl() + "get") {
            parameter("q", query)
            parameter("langpair", "${sourceOrAuto(source)}|$target")
            parameter("key", getApiKey()?.ifBlank { null })
        }.bodyAsText()

        val response = TranslationHttpClient.json.decodeFromString<MMTranslationResponse>(body)
        if (response.responseStatus != null && response.responseStatus >= 400) {
            throw IllegalStateException(
                response.responseDetails?.takeIf { it.isNotBlank() }
                    ?: "MyMemory returned HTTP ${response.responseStatus}",
            )
        }
        val translatedText = response.responseData?.translatedText.orEmpty()
        if (translatedText.isBlank()) {
            throw IllegalStateException("MyMemory answered without a translation")
        }
        return Translation(
            translatedText = translatedText,
            detectedLanguage = response.responseData?.detectedLanguage,
        )
    }
}