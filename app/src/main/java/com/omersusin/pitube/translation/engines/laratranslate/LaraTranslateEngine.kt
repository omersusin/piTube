package com.omersusin.pitube.translation.engines.laratranslate

import com.omersusin.pitube.translation.ApiKeyState
import com.omersusin.pitube.translation.EngineSettingsProvider
import com.omersusin.pitube.translation.Language
import com.omersusin.pitube.translation.Translation
import com.omersusin.pitube.translation.TranslationEngine
import com.omersusin.pitube.translation.TranslationHttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LaraTranslateRequest(
    val q: String,
    val source: String,
    val target: String,
)

@Serializable
data class LaraTranslateResponse(
    val content: LaraTranslateContent,
    val status: Int,
)

@Serializable
data class LaraTranslateContent(
    @SerialName("source_language") val sourceLanguage: String,
    val translation: String,
)

@Serializable
data class LaraLanguagesResponse(
    val data: LaraLanguagesData,
)

@Serializable
data class LaraLanguagesData(
    val content: LaraLanguagesContent,
)

@Serializable
data class LaraLanguagesContent(
    val body: String,
)

/**
 * LaraTranslate - free web translator. The supported languages are listed
 * on the docs site, so they're scraped from the HTML list. Ported from
 * Translate You's LaEngine (GPL-3.0).
 */
class LaraTranslateEngine(settingsProvider: EngineSettingsProvider) : TranslationEngine(settingsProvider) {

    override val name: String = "LaraTranslate"

    override val defaultUrl: String = "https://webapi.laratranslate.com"

    override val urlModifiable: Boolean = false

    override val apiKeyState: ApiKeyState = ApiKeyState.DISABLED

    override val autoLanguageCode: String? = ""

    private val langRegex = Regex("""<li>(?<name>.*?) - `(?<code>.*?)`</li>""")

    override suspend fun getLanguages(): List<Language> {
        val languagesHtml = TranslationHttpClient.json.decodeFromString<LaraLanguagesResponse>(
            TranslationHttpClient.client.get(LANGUAGES_LIST_URL).bodyAsText(),
        ).data.content.body

        return langRegex.findAll(languagesHtml).map {
            Language(
                code = it.groups["code"]!!.value.substringBefore("-"), // remove country info
                name = it.groups["name"]!!.value.substringBefore("(").trim(),
            )
        }
            .toList()
            .distinct()
    }

    override suspend fun translate(query: String, source: String, target: String): Translation {
        val requestBody = LaraTranslateRequest(
            q = query,
            source = sourceOrAuto(source),
            target = target,
        )
        val translation = TranslationHttpClient.json.decodeFromString<LaraTranslateResponse>(
            TranslationHttpClient.client.post(url("translate")) {
                contentType(ContentType.Application.Json)
                setBody(TranslationHttpClient.json.encodeToString(LaraTranslateRequest.serializer(), requestBody))
            }.bodyAsText(),
        )

        if (translation.status != 200) throw Exception("Received error response from LaraTranslate.")

        return Translation(
            translatedText = translation.content.translation,
            detectedLanguage = translation.content.sourceLanguage,
        )
    }

    companion object {
        private const val LANGUAGES_LIST_URL =
            "https://developers.laratranslate.com/lara/api-next/v2/versions/1.5/guides/supported-languages"
    }
}