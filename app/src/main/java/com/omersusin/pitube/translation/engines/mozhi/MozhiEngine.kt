package com.omersusin.pitube.translation.engines.mozhi

import com.omersusin.pitube.translation.ApiKeyState
import com.omersusin.pitube.translation.CommonLanguages
import com.omersusin.pitube.translation.EngineSettingsProvider
import com.omersusin.pitube.translation.Language
import com.omersusin.pitube.translation.Translation
import com.omersusin.pitube.translation.TranslationEngine
import com.omersusin.pitube.translation.TranslationHttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MhTranslationResponse(
    @SerialName("pronunciation")
    val pronunciation: String? = null,
    @SerialName("detected")
    val detectedLanguage: String? = null,
    @SerialName("translated-text")
    val translatedText: String = "",
)

@Serializable
data class MhLanguage(
    @SerialName("Id")
    val id: String = "",
    @SerialName("Name")
    val name: String = "",
)

/**
 * Mozhi - free public aggregator over Google, Libre, Reverso, DeepL,
 * DuckDuckGo, MyMemory, Watson and Yandex (picked via the model selector).
 * Multipart form requests, ported from Translate You's MhEngine (GPL-3.0).
 */
class MozhiEngine(settingsProvider: EngineSettingsProvider) : TranslationEngine(settingsProvider) {

    override val name: String = "Mozhi"

    override val defaultUrl: String = "https://mozhi.aryak.me/"

    override val urlModifiable: Boolean = true

    override val apiKeyState: ApiKeyState = ApiKeyState.DISABLED

    override val autoLanguageCode: String? = "auto"

    override val supportedModels: List<String> = listOf(
        "google",
        "libre",
        "reverso",
        "deepl",
        "duckduckgo",
        "mymemory",
        "watson",
        "yandex",
    )

    override suspend fun getLanguages(): List<Language> {
        return runCatching {
            val body = TranslationHttpClient.client.get(url("api/target_languages/")) {
                parameter("engine", effectiveModel())
            }.bodyAsText()
            TranslationHttpClient.json.decodeFromString<List<MhLanguage>>(body)
                .map { Language(it.id, it.name) }
        }.getOrElse { CommonLanguages.languages }
    }

    override suspend fun translate(query: String, source: String, target: String): Translation {
        val responseText = TranslationHttpClient.client.submitFormWithBinaryData(
            url = url("api/translate/"),
            formData = formData {
                append("engine", effectiveModel().orEmpty())
                append("from", sourceOrAuto(source.take(2)))
                append("to", target.take(2))
                append("text", query)
            },
        ).bodyAsText()

        val response = TranslationHttpClient.json.decodeFromString<MhTranslationResponse>(responseText)
        return Translation(
            translatedText = response.translatedText,
            detectedLanguage = response.detectedLanguage?.takeIf { it.isNotBlank() },
        )
    }
}