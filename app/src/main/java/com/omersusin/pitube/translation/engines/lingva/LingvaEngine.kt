package com.omersusin.pitube.translation.engines.lingva

import android.net.Uri
import com.omersusin.pitube.translation.ApiKeyState
import com.omersusin.pitube.translation.CommonLanguages
import com.omersusin.pitube.translation.EngineSettingsProvider
import com.omersusin.pitube.translation.Language
import com.omersusin.pitube.translation.Translation
import com.omersusin.pitube.translation.TranslationEngine
import com.omersusin.pitube.translation.TranslationHttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable

@Serializable
data class LvLanguageResponse(val languages: List<Language> = emptyList())

@Serializable
data class LvTranslationResponse(
    val translation: String = "",
    val info: LvTranslationInfo? = null,
)

@Serializable
data class LvTranslationInfo(
    val detectedSource: String? = null,
    val pronunciation: LvPronunciation? = null,
    val similar: List<String>? = null,
    val examples: List<String>? = null,
)

@Serializable
data class LvPronunciation(val query: String? = null)

@Serializable
data class LvAudioResponse(val audio: List<Int> = emptyList()) {
    fun toByteArray() = audio.map { it.toByte() }.toByteArray()
}

/**
 * Lingva - free web-mirror front end with the query embedded in the URL path.
 * Ported from Translate You's LVEngine (GPL-3.0).
 */
class LingvaEngine(settingsProvider: EngineSettingsProvider) : TranslationEngine(settingsProvider) {

    override val name: String = "Lingva"

    override val defaultUrl: String = "https://lingva.ml"

    override val urlModifiable: Boolean = true

    override val apiKeyState: ApiKeyState = ApiKeyState.DISABLED

    override val autoLanguageCode: String? = "auto"

    override val supportsAudio: Boolean = true

    override val statusNote: String =
        "Default lingva.ml instance is often down; use a self-hosted instance."

    override suspend fun getLanguages(): List<Language> {
        return runCatching {
            val body = TranslationHttpClient.client.get(url("api/v1/languages")).bodyAsText()
            val languages = TranslationHttpClient.json
                .decodeFromString<LvLanguageResponse>(body)
                .languages
            languages.drop(1)
        }.getOrElse { CommonLanguages.languages }
    }

    override suspend fun translate(query: String, source: String, target: String): Translation {
        val encodedQuery = Uri.encode(query.replace("/", ""))
        val requestUrl = url("api/v1/${sourceOrAuto(source)}/$target/$encodedQuery")
        val body = TranslationHttpClient.client.get(requestUrl).bodyAsText()
        val response = TranslationHttpClient.json.decodeFromString<LvTranslationResponse>(body)
        return Translation(
            translatedText = response.translation,
            detectedLanguage = response.info?.detectedSource,
            similar = response.info?.similar,
            examples = response.info?.examples,
        )
    }

    override suspend fun getAudioFile(lang: String, query: String): ByteArray? {
        val encodedQuery = Uri.encode(query.replace("/", ""))
        val body = TranslationHttpClient.client
            .get(url("api/v1/audio/$lang/$encodedQuery"))
            .bodyAsText()
        return TranslationHttpClient.json
            .decodeFromString<LvAudioResponse>(body)
            .toByteArray()
    }
}