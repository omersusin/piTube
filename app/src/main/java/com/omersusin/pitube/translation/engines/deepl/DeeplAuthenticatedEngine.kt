package com.omersusin.pitube.translation.engines.deepl

import com.omersusin.pitube.translation.ApiKeyState
import com.omersusin.pitube.translation.EngineSettingsProvider
import com.omersusin.pitube.translation.Language
import com.omersusin.pitube.translation.Translation
import com.omersusin.pitube.translation.TranslationEngine
import com.omersusin.pitube.translation.TranslationHttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class DeeplLanguage(
    val language: String = "",
    val name: String = "",
)

@Serializable
private data class DeeplTranslation(
    @SerialName("detected_source_language")
    val detectedSourceLanguage: String = "",
    val text: String = "",
)

@Serializable
private data class DeeplTranslationResponse(
    val translations: List<DeeplTranslation> = listOf(),
)

/**
 * DeepL authenticated API engines - one entry per account tier, exactly as
 * Translate You ships them: the free tier endpoint and the paid endpoint are
 * separate providers with the same key field. The supported languages come
 * from the API itself ([v2/languages]), unlike the free engines that use the
 * bundled common list. Ported from Translate You's DeeplAuthenticatedEngine
 * (GPL-3.0).
 */
abstract class DeeplAuthenticatedEngine(settingsProvider: EngineSettingsProvider) :
    TranslationEngine(settingsProvider) {

    override val urlModifiable: Boolean = false

    override val apiKeyState: ApiKeyState = ApiKeyState.REQUIRED

    override val autoLanguageCode: String? = ""

    private val apiKeyString: String
        get() = "DeepL-Auth-Key ${getApiKey() ?: ""}"

    override suspend fun getLanguages(): List<Language> {
        val body = TranslationHttpClient.client.get(url("v2/languages")) {
            header(HttpHeaders.Authorization, apiKeyString)
        }.bodyAsText()
        return TranslationHttpClient.json.decodeFromString<List<DeeplLanguage>>(body).map {
            Language(code = it.language.lowercase(), name = it.name)
        }
    }

    override suspend fun translate(query: String, source: String, target: String): Translation {
        val body = TranslationHttpClient.client.post(url("v2/translate")) {
            header(HttpHeaders.Authorization, apiKeyString)
            parameter("source_lang", sourceOrAuto(source.uppercase()))
            parameter("target_lang", target.uppercase())
            parameter("text", query)
        }
        if (body.status.value !in 200..299) {
            throw IllegalStateException("DeepL returned HTTP ${body.status.value}")
        }
        val response = TranslationHttpClient.json
            .decodeFromString<DeeplTranslationResponse>(body.bodyAsText())
        val translatedText = response.translations.firstOrNull()?.text.orEmpty()
        if (translatedText.isBlank()) {
            throw IllegalStateException("DeepL answered without a translation")
        }
        return Translation(
            translatedText = translatedText,
            detectedLanguage = response.translations.firstOrNull()?.detectedSourceLanguage,
        )
    }
}

class DeeplAuthenticatedFreeApiEngine(settingsProvider: EngineSettingsProvider) :
    DeeplAuthenticatedEngine(settingsProvider) {

    override val name: String = "DeepL (Authenticated, free API)"

    override val defaultUrl: String = "https://api-free.deepl.com"
}

class DeeplAuthenticatedPaidApiEngine(settingsProvider: EngineSettingsProvider) :
    DeeplAuthenticatedEngine(settingsProvider) {

    override val name: String = "DeepL (Authenticated, paid API)"

    override val defaultUrl: String = "https://api.deepl.com"
}