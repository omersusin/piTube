package com.omersusin.pitube.translation.engines.deepl

import com.omersusin.pitube.translation.ApiKeyState
import com.omersusin.pitube.translation.CommonLanguages
import com.omersusin.pitube.translation.EngineSettingsProvider
import com.omersusin.pitube.translation.Language
import com.omersusin.pitube.translation.Translation
import com.omersusin.pitube.translation.TranslationEngine
import com.omersusin.pitube.translation.TranslationHttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

@Serializable
private data class DeeplTranslation(val text: String = "", val detectedSourceLanguage: String? = null)

@Serializable
private data class DeeplTranslateResponse(val translations: List<DeeplTranslation> = emptyList())

@Serializable
private data class DeeplErrorBody(val message: String? = null)

/**
 * Native DeepL API provider. Free keys (`:fx` suffix) automatically use the
 * free endpoint. Ported from ViVi Music's DeepLService (GPL-3.0).
 */
class DeeplEngine(settingsProvider: EngineSettingsProvider) : TranslationEngine(settingsProvider) {

    override val name: String = "DeepL"

    override val defaultUrl: String = "https://api.deepl.com/v2/translate"

    override val urlModifiable: Boolean = false

    override val apiKeyState: ApiKeyState = ApiKeyState.REQUIRED

    override val autoLanguageCode: String? = ""

    override suspend fun getLanguages(): List<Language> = CommonLanguages.languages

    override suspend fun translate(query: String, source: String, target: String): Translation {
        val apiKey = getApiKey().orEmpty().trim().ifEmpty {
            throw IllegalStateException("An API key is required for DeepL. Add it in Translation settings.")
        }
        val endpoint = if (apiKey.endsWith(":fx")) {
            "https://api-free.deepl.com/v2/translate"
        } else {
            "https://api.deepl.com/v2/translate"
        }

        val targetLang = toDeeplCode(target)
        val sourceLang = source.takeIf { it.isNotBlank() }?.let { toDeeplCode(it) }

        val body = buildJsonObject {
            putJsonArray("text") { add(JsonPrimitive(query)) }
            put("target_lang", targetLang)
            if (!sourceLang.isNullOrBlank()) put("source_lang", sourceLang)
            put("preserve_formatting", true)
        }

        val response = TranslationHttpClient.client.post(endpoint) {
            header(HttpHeaders.Authorization, "DeepL-Auth-Key $apiKey")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val bodyText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val serverMessage = runCatching {
                TranslationHttpClient.json
                    .decodeFromString<DeeplErrorBody>(bodyText)
                    .message
            }.getOrNull()
            throw IllegalStateException(
                serverMessage
                    ?: "DeepL returned HTTP ${response.status.value}",
            )
        }
        val parsed = runCatching {
            TranslationHttpClient.json.decodeFromString<DeeplTranslateResponse>(bodyText)
        }.getOrNull()
        val translation = parsed?.translations?.firstOrNull()
            ?: throw IllegalStateException("DeepL answered without a translation")

        return Translation(
            translatedText = translation.text,
            detectedLanguage = translation.detectedSourceLanguage?.lowercase(),
        )
    }

    /** ISO code -> DeepL target language code (ViVi's mapping). */
    private fun toDeeplCode(code: String): String {
        val normalized = code.lowercase().trim()
        return when (normalized) {
            "zh", "zh-cn", "zh-hans" -> "ZH"
            "en-gb" -> "EN-GB"
            "pt" -> "PT-PT"
            "pt-br" -> "PT-BR"
            else -> code.uppercase().take(2)
        }
    }
}