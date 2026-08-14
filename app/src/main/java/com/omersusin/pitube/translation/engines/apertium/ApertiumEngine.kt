package com.omersusin.pitube.translation.engines.apertium

import com.omersusin.pitube.translation.ApiKeyState
import com.omersusin.pitube.translation.EngineSettingsProvider
import com.omersusin.pitube.translation.Language
import com.omersusin.pitube.translation.Translation
import com.omersusin.pitube.translation.TranslationEngine
import com.omersusin.pitube.translation.TranslationHttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class ApertiumLanguagePair(
    val sourceLanguage: String,
    val targetLanguage: String,
)

@Serializable
data class ApertiumLanguagesResponse(
    val responseData: List<ApertiumLanguagePair>,
)

@Serializable
data class ApertiumResponse(
    val responseData: ApertumResponseData,
    val responseStatus: Int,
)

@Serializable
data class ApertumResponseData(
    val translatedText: String,
)

/**
 * Apertium - free rule-based machine translation platform. Only supports
 * ISO 639-3 codes, so the two-letter codes are mapped via [Locale].
 * Ported from Translate You's ApEngine (GPL-3.0).
 */
class ApertiumEngine(settingsProvider: EngineSettingsProvider) : TranslationEngine(settingsProvider) {

    override val name: String = "Apertium"

    override val defaultUrl: String = "https://apertium.org"

    override val urlModifiable: Boolean = false

    override val apiKeyState: ApiKeyState = ApiKeyState.DISABLED

    override val autoLanguageCode: String? = null

    private val iso3ToIso2Map: Map<String, String> by lazy {
        Locale.getISOLanguages().associate { iso2 ->
            Locale.forLanguageTag(iso2).isO3Language to iso2
        }
    }

    private val iso2ToIso3Map: Map<String, String> by lazy {
        Locale.getISOLanguages().associateWith { iso2 ->
            Locale.forLanguageTag(iso2).isO3Language
        }
    }

    override suspend fun getLanguages(): List<Language> {
        val body = TranslationHttpClient.client.get(url("apy/listPairs")).bodyAsText()
        return TranslationHttpClient.json.decodeFromString<ApertiumLanguagesResponse>(body).responseData
            .flatMap { listOf(it.sourceLanguage, it.targetLanguage) }
            .distinct()
            .map {
                val code = iso3ToIso2Map[it] ?: return@map null
                val locale = Locale.forLanguageTag(code)
                Language(
                    code = code,
                    name = locale.getDisplayName(locale),
                )
            }
            .filterNotNull()
    }

    override suspend fun translate(query: String, source: String, target: String): Translation {
        // Apertium can only deal with 3 letter language codes, hence converting them here
        val source3 = iso2ToIso3Map[source] ?: throw IllegalArgumentException("unsupported languages $source")
        val target3 = iso2ToIso3Map[target] ?: throw IllegalArgumentException("unsupported language $target")

        val body = TranslationHttpClient.client.submitFormWithBinaryData(
            url = url("apy/translate"),
            formData = formData {
                append("langpair", "$source3|$target3")
                append("q", query)
                append("markUnknown", "no")
                append("prefs", "")
            },
        ).bodyAsText()

        return Translation(
            TranslationHttpClient.json.decodeFromString<ApertiumResponse>(body).responseData.translatedText,
        )
    }
}