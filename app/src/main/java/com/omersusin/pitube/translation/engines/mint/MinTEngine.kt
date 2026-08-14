package com.omersusin.pitube.translation.engines.mint

import com.omersusin.pitube.translation.ApiKeyState
import com.omersusin.pitube.translation.CommonLanguages
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale

@Serializable
data class WmTranslationResponse(
    @SerialName("sourcelanguage")
    val sourceLanguage: String? = null,
    @SerialName("targetlanguage")
    val targetLanguage: String? = null,
    val translation: String = "",
)

/**
 * Wikimedia MinT - Wikimedia's free neural machine translation service.
 * No automatic source detection. Ported from Translate You's WmEngine
 * (GPL-3.0); the languages endpoint answers a nested map keyed by codes.
 */
class MinTEngine(settingsProvider: EngineSettingsProvider) : TranslationEngine(settingsProvider) {

    override val name: String = "MinT"

    override val defaultUrl: String = "https://translate.wmcloud.org/"

    override val urlModifiable: Boolean = true

    override val apiKeyState: ApiKeyState = ApiKeyState.DISABLED

    override val autoLanguageCode: String? = null

    override suspend fun getLanguages(): List<Language> {
        return runCatching {
            val body = TranslationHttpClient.client
                .get(url("api/languages"))
                .bodyAsText()
            val map = TranslationHttpClient.json
                .decodeFromString<Map<String, Map<String, List<String>>>>(body)
            map.keys.mapNotNull { code ->
                val displayName = runCatching {
                    Locale.forLanguageTag(code).displayLanguage
                }.getOrNull().takeIf { !it.isNullOrBlank() }
                Language(code, displayName ?: code)
            }
        }.getOrElse { CommonLanguages.languages }
    }

    override suspend fun translate(query: String, source: String, target: String): Translation {
        // MinT has no "auto" source; guess from the text's dominant script.
        val effectiveSource = source.takeIf { it.isNotBlank() } ?: guessSource(query)
        val body = buildJsonObject { put("text", query) }
        val responseText = TranslationHttpClient.client.post(
            url("api/translate/$effectiveSource/$target"),
        ) {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }.bodyAsText()

        val response = TranslationHttpClient.json
            .decodeFromString<WmTranslationResponse>(responseText)
        return Translation(translatedText = response.translation)
    }

    /**
     * Best-effort source-language guess from the dominant Unicode script.
     * Latin-script text is assumed to be English (by far the most common
     * source on YouTube).
     */
    private fun guessSource(text: String): String {
        val dominant = com.omersusin.pitube.data.translation.LanguageScriptUtil.dominantScript(text)
        return when (dominant) {
            Character.UnicodeScript.CYRILLIC -> "ru"
            Character.UnicodeScript.ARABIC -> "ar"
            Character.UnicodeScript.HEBREW -> "he"
            Character.UnicodeScript.GREEK -> "el"
            Character.UnicodeScript.HAN -> "zh"
            Character.UnicodeScript.HANGUL -> "ko"
            Character.UnicodeScript.DEVANAGARI -> "hi"
            Character.UnicodeScript.THAI -> "th"
            Character.UnicodeScript.TAMIL -> "ta"
            Character.UnicodeScript.KHMER -> "km"
            Character.UnicodeScript.LAO -> "lo"
            else -> "en"
        }
    }
}