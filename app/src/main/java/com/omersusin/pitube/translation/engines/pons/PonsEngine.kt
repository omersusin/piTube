package com.omersusin.pitube.translation.engines.pons

import com.omersusin.pitube.translation.ApiKeyState
import com.omersusin.pitube.translation.EngineSettingsProvider
import com.omersusin.pitube.translation.Language
import com.omersusin.pitube.translation.Translation
import com.omersusin.pitube.translation.TranslationEngine
import com.omersusin.pitube.translation.TranslationHttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
data class PonsLanguages(
    val languages: Map<String, PonsLanguage>,
)

@Serializable
data class PonsLanguage(
    val dir: String,
    val display: String,
)

@Serializable
data class PonsData(
    val sourceLanguage: String?,
    val targetLanguage: String,
    val text: String,
)

/**
 * Pons - dictionary publisher's free text-translation web API. Ported from
 * Translate You's PonsEngine (GPL-3.0).
 */
class PonsEngine(settingsProvider: EngineSettingsProvider) : TranslationEngine(settingsProvider) {

    override val name: String = "Pons"

    override val defaultUrl: String = "https://api.pons.com"

    override val urlModifiable: Boolean = false

    override val apiKeyState: ApiKeyState = ApiKeyState.DISABLED

    override val autoLanguageCode: String? = ""

    override suspend fun getLanguages(): List<Language> {
        val body = TranslationHttpClient.client.get(url("text-translation-web/v4/languages")) {
            parameter("locale", "en")
        }.bodyAsText()
        return TranslationHttpClient.json.decodeFromString<PonsLanguages>(body).languages.map { (code, langInfo) ->
            Language(code = code, name = langInfo.display)
        }
    }

    override suspend fun translate(query: String, source: String, target: String): Translation {
        val requestBody = PonsData(source.takeIf { it.isNotEmpty() }, target, query)
        val response = TranslationHttpClient.json.decodeFromString<PonsData>(
            TranslationHttpClient.client.post(url("text-translation-web/v4/translate")) {
                parameter("locale", "en")
                contentType(ContentType.Application.Json)
                setBody(TranslationHttpClient.json.encodeToString(PonsData.serializer(), requestBody))
            }.bodyAsText(),
        )
        return Translation(response.text, response.sourceLanguage)
    }
}