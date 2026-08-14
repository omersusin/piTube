package com.omersusin.pitube.translation.engines.onering

import com.omersusin.pitube.translation.ApiKeyState
import com.omersusin.pitube.translation.EngineSettingsProvider
import com.omersusin.pitube.translation.Language
import com.omersusin.pitube.translation.Translation
import com.omersusin.pitube.translation.TranslationEngine
import com.omersusin.pitube.translation.TranslationHttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class OneRingResponse(
    val result: String = "",
)

/**
 * OneRing - self-hostable machine-translation gateway with many plugins
 * (NLLB, Opus, MBART, ChatGPT, DeepL, ...) picked via the model selector.
 * The instance URL is user-supplied (Translate You ships no public one), the
 * API key is optional. Ported from Translate You's OneRingEngine (GPL-3.0).
 */
class OneRingEngine(settingsProvider: EngineSettingsProvider) : TranslationEngine(settingsProvider) {

    override val name: String = "OneRing"

    override val defaultUrl: String = "https://your.instance.domain"

    override val urlModifiable: Boolean = true

    override val apiKeyState: ApiKeyState = ApiKeyState.OPTIONAL

    override val autoLanguageCode: String? = ""

    override val supportedModels: List<String> = listOf(
        "no_translate2",
        "no_translate",
        "fb_nllb_ctranslate2",
        "bloomz",
        "vsegpt_chat",
        "fb_nllb_translate",
        "opus_mt",
        "google_translate",
        "deepl",
        "deepl_translate",
        "use_mid_lang",
        "fb_mbart50",
        "openai_chat",
        "libre_translate",
        "koboldapi_translate",
        "lingvanex",
        "multi_sources",
    )

    override suspend fun getLanguages(): List<Language> {
        return Locale.getAvailableLocales()
            .map { Language(it.language, it.getDisplayName(Locale.getDefault())) }
            .distinctBy { it.code }
    }

    override suspend fun translate(query: String, source: String, target: String): Translation {
        val body = TranslationHttpClient.client.get(url("translate")) {
            parameter("text", query)
            parameter("from_lang", source)
            parameter("to", target)
            parameter("translator_plugin", getSelectedModel() ?: throw IllegalArgumentException("No model selected"))
            parameter("api_key", getApiKey())
        }.bodyAsText()
        val response = TranslationHttpClient.json.decodeFromString<OneRingResponse>(body)
        return Translation(response.result)
    }
}