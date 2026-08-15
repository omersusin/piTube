package com.omersusin.pitube.translation.engines.mozhi

import com.omersusin.pitube.translation.ApiKeyState
import com.omersusin.pitube.translation.CommonLanguages
import com.omersusin.pitube.translation.Definition
import com.omersusin.pitube.translation.EngineSettingsProvider
import com.omersusin.pitube.translation.Language
import com.omersusin.pitube.translation.Translation
import com.omersusin.pitube.translation.TranslationEngine
import com.omersusin.pitube.translation.TranslationHttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsBytes
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
    @SerialName("source_synonyms") val sourceSynonyms: List<String>? = null,
    @SerialName("source_transliteration") val sourceTransliteration: String? = null,
    @SerialName("target_synonyms") val targetSynonyms: List<String>? = null,
    @SerialName("target_transliteration") val targetTransliteration: String? = null,
    @SerialName("word_choices") val wordChoices: List<WordChoice>? = null,
)

@Serializable
data class WordChoice(
    val definition: String? = null,
    val example: String? = null,
    val word: String? = null,
    @SerialName("examples_source") val examplesSource: List<String>? = null,
    @SerialName("examples_target") val examplesTarget: List<String>? = null,
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
 *
 * The current Mozhi API is an unauthenticated GET contract: `api/translate`
 * and `api/source_languages` / `api/target_languages` all take plain query
 * parameters (the historical multipart POST is gone, which broke earlier
 * versions of this port). A growing number of public instances put translate
 * behind a paid key; when one is set it is sent as the `api_key` query
 * parameter and reported as optional, identical to Translate You's MhEngine.
 */
class MozhiEngine(settingsProvider: EngineSettingsProvider) : TranslationEngine(settingsProvider) {

    override val name: String = "Mozhi"

    // mozhi.aryak.me's translate endpoint is currently broken server-side
    // ("invalid json" on every request); trnslt.oddte.ch is a healthy
    // instance of the same build. Still user-overridable.
    override val defaultUrl: String = "https://trnslt.oddte.ch/"

    override val urlModifiable: Boolean = true

    override val apiKeyState: ApiKeyState = ApiKeyState.OPTIONAL

    override val autoLanguageCode: String? = "auto"

    override val supportsAudio: Boolean = true

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
            val translations = TranslationHttpClient.client.get(url("api/target_languages")) {
                parameter("engine", effectiveModel())
            }.bodyAsText()
            val targets = TranslationHttpClient.json
                .decodeFromString<List<MhLanguage>>(translations)
                .map { Language(it.id, it.name) }

            // Source languages include an "auto" (detect) entry; offered as a
            // first option so the picker exposes automatic detection.
            val sources = runCatching {
                TranslationHttpClient.client.get(url("api/source_languages")) {
                    parameter("engine", effectiveModel())
                }.bodyAsText()
            }.getOrNull().let { body ->
                body?.let {
                    TranslationHttpClient.json.decodeFromString<List<MhLanguage>>(it)
                        .map { source -> Language(source.id, source.name) }
                }.orEmpty()
            }
            val autoSource = sources.firstOrNull { it.code == "auto" }
            (listOfNotNull(autoSource) + targets).distinctBy { it.code }
        }.getOrElse { CommonLanguages.languages }
    }

    override suspend fun translate(query: String, source: String, target: String): Translation {
        val apiKey = getApiKey()
        val responseText = TranslationHttpClient.client.get(url("api/translate")) {
            parameter("engine", effectiveModel())
            if (!apiKey.isNullOrBlank()) parameter("api_key", apiKey)
            parameter("from", sourceOrAuto(source).ifBlank { "auto" })
            parameter("to", target)
            parameter("text", query)
        }.bodyAsText()

        val response = TranslationHttpClient.json.decodeFromString<MhTranslationResponse>(responseText)
        return Translation(
            translatedText = response.translatedText,
            transliterations = listOf(
                response.sourceTransliteration,
                response.targetTransliteration,
            ).mapNotNull { transliteration ->
                transliteration?.takeIf {
                    it.isNotBlank() && !it.matches(transliterationFailedRegex)
                }
            },
            detectedLanguage = response.detectedLanguage?.takeIf { it.isNotBlank() },
            definitions = response.wordChoices.orEmpty().map { definition ->
                Definition(
                    definition = definition.definition?.takeIf { it.isNotBlank() },
                    example = definition.example?.takeIf { it.isNotBlank() },
                )
            }.takeIf { it.isNotEmpty() },
            similar = response.targetSynonyms,
            examples = response.wordChoices.orEmpty()
                .flatMap { it.examplesSource.orEmpty() + it.examplesTarget.orEmpty() }
                .map { it.replace(bracketRegex, "") },
        )
    }

    private val transliterationFailedRegex = Regex("Direction '\\w{2}' is not supported")
    private val bracketRegex = Regex("[<>]")

    override suspend fun getAudioFile(lang: String, query: String): ByteArray? {
        return TranslationHttpClient.client.get(url("api/tts")) {
            parameter("engine", "google")
            parameter("lang", lang)
            parameter("text", query)
        }.bodyAsBytes()
    }
}