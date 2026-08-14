package com.omersusin.pitube.translation.engines.glosbe

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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GlLanguagesResponse(
    val popularLanguages: List<GlLanguage>,
    val otherLanguages: List<GlLanguage>,
)

@Serializable
data class GlLanguage(
    val code: String,
    @SerialName("iso693_3") val isoCode: String,
    val name: String,
    val alternativeNames: List<String>,
)

@Serializable
data class GlTranslationResponse(
    val input: String,
    val translation: String,
)

/**
 * Glosbe - community dictionary, provides machine translation free of
 * charge. The request body is a plain JSON string of the text (same shape
 * as the upstream Retrofit String body). Ported from Translate You's
 * GlEngine (GPL-3.0).
 */
class GlosbeEngine(settingsProvider: EngineSettingsProvider) : TranslationEngine(settingsProvider) {

    override val name: String = "Glosbe"

    override val defaultUrl: String = "https://translator-api.glosbe.com/"

    override val urlModifiable: Boolean = false

    override val apiKeyState: ApiKeyState = ApiKeyState.DISABLED

    override val autoLanguageCode: String? = null

    override suspend fun getLanguages(): List<Language> {
        val body = TranslationHttpClient.client.get("https://iapi.glosbe.com/iapi3/languages").bodyAsText()
        return TranslationHttpClient.json.decodeFromString<GlLanguagesResponse>(body).popularLanguages.map {
            Language(it.code, it.name)
        }
    }

    override suspend fun translate(query: String, source: String, target: String): Translation {
        val response = TranslationHttpClient.json.decodeFromString<GlTranslationResponse>(
            TranslationHttpClient.client.post(url("translateByLangDetect")) {
                parameter("sourceLang", source)
                parameter("targetLang", target)
                contentType(ContentType.Application.Json)
                setBody(TranslationHttpClient.json.encodeToString(query))
            }.bodyAsText(),
        )
        return Translation(response.translation)
    }
}