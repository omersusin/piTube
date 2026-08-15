package com.omersusin.pitube.translation.engines.libretranslate

import com.omersusin.pitube.translation.ApiKeyState
import com.omersusin.pitube.translation.CommonLanguages
import com.omersusin.pitube.translation.EngineSettingsProvider
import com.omersusin.pitube.translation.Language
import com.omersusin.pitube.translation.Translation
import com.omersusin.pitube.translation.TranslationEngine
import com.omersusin.pitube.translation.TranslationHttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable

@Serializable
data class LTTranslation(
    val translatedText: String = "",
    val detectedLanguage: LTDetectedLanguage? = null,
    val alternatives: List<String> = emptyList(),
)

@Serializable
data class LTDetectedLanguage(val language: String? = null)

@Serializable
data class LTLanguage(
    val code: String? = null,
    val name: String? = null,
)

/**
 * LibreTranslate - free, self-hostable engine with an optional API key.
 * Ported from Translate You's LTEngine (GPL-3.0); query parameters ride in
 * the URL, mirroring what the upstream Retrofit client actually sends.
 */
class LibreTranslateEngine(settingsProvider: EngineSettingsProvider) : TranslationEngine(settingsProvider) {

    override val name: String = "LibreTranslate"

    override val defaultUrl: String = "https://libretranslate.com"

    override val urlModifiable: Boolean = true

    override val apiKeyState: ApiKeyState = ApiKeyState.OPTIONAL

    override val autoLanguageCode: String? = "auto"

    override val statusNote: String =
        "Public instances are key-gated or rate-limited; self-host LibreTranslate for reliable use."

    override suspend fun getLanguages(): List<Language> {
        return runCatching {
            val body = TranslationHttpClient.client.get(url("languages")).bodyAsText()
            TranslationHttpClient.json.decodeFromString<List<LTLanguage>>(body)
                .mapNotNull { l ->
                    l.code?.takeIf { it.isNotBlank() }?.let { Language(it, l.name ?: it) }
                }
        }.getOrElse { CommonLanguages.languages }
    }

    override suspend fun translate(query: String, source: String, target: String): Translation {
        val responseText = TranslationHttpClient.client.post(url("translate")) {
            parameter("q", query)
            parameter("source", sourceOrAuto(source))
            parameter("target", target)
            parameter("api_key", getApiKey()?.ifBlank { null })
            parameter("format", "text")
            parameter("alternatives", 3)
        }.bodyAsText()

        val response = TranslationHttpClient.json.decodeFromString<LTTranslation>(responseText)
        return Translation(
            translatedText = response.translatedText,
            detectedLanguage = response.detectedLanguage?.language,
            alternativeTranslations = response.alternatives.takeIf { it.isNotEmpty() },
        )
    }
}