package com.omersusin.pitube.translation.engines.kagi

import com.omersusin.pitube.translation.ApiKeyState
import com.omersusin.pitube.translation.Definition
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
data class KagiLanguage(
    val language: String = "",
    val name: String = "",
    @SerialName("supports_formality") val supportsFormality: Boolean = false,
)

@Serializable
data class KagiTranslationRequest(
    val text: String,
    @SerialName("source_lang") val sourceLang: String,
    @SerialName("target_lang") val targetLang: String,
    @SerialName("skip_definition") val skipDefinition: Boolean = false,
    val model: String = "standard",
)

@Serializable
data class KagiDetectedLanguage(
    val iso: String = "",
    val label: String = "",
)

@Serializable
data class KagiMeaning(
    val definition: String = "",
    @SerialName("part_of_speech") val partOfSpeech: List<String> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val dialect: List<String> = emptyList(),
    @SerialName("usage_note") val usageNote: String? = null,
    @SerialName("usage_level") val usageLevel: List<String> = emptyList(),
)

@Serializable
data class KagiDefinition(
    val word: String = "",
    val language: String = "",
    @SerialName("primary_meaning") val primaryMeaning: KagiMeaning? = null,
    @SerialName("secondary_meanings") val secondaryMeanings: List<KagiMeaning> = emptyList(),
    val examples: List<String> = emptyList(),
    val pronunciation: String? = null,
)

@Serializable
data class KagiTranslationResponse(
    val translation: String = "",
    @SerialName("detected_language") val detectedLanguage: KagiDetectedLanguage? = null,
    val definition: KagiDefinition? = null,
)

/**
 * Kagi Translate - paid, requires a session token. Ported from Translate
 * You's KagiEngine (GPL-3.0). The model presets double as feature switches:
 * "with definitions" requests the dictionary, "Best model" upgrades the
 * backend model.
 */
class KagiEngine(settingsProvider: EngineSettingsProvider) : TranslationEngine(settingsProvider) {

    override val name: String = "Kagi"

    override val defaultUrl: String = "https://translate.kagi.com"

    override val urlModifiable: Boolean = false

    override val apiKeyState: ApiKeyState = ApiKeyState.REQUIRED

    override val autoLanguageCode: String? = "auto"

    override val supportedModels: List<String> = listOf(
        "Fastest model",
        "Fastest model with definitions",
        "Best model",
        "Best model with definitions",
    )

    override suspend fun getLanguages(): List<Language> {
        val body = TranslationHttpClient.client.get(url("api/list-languages"))
            .bodyAsText()
        return TranslationHttpClient.json.decodeFromString<List<KagiLanguage>>(body)
            .map { Language(it.language.lowercase(), it.name) }
    }

    override suspend fun translate(query: String, source: String, target: String): Translation {
        val model = getSelectedModel() ?: throw IllegalArgumentException("No model selected")

        val fetchDefinitions = model.contains("definitions")
        val useBestModel = model.contains("Best")

        val request = KagiTranslationRequest(
            text = query,
            sourceLang = sourceOrAuto(source),
            targetLang = target,
            skipDefinition = !fetchDefinitions,
            model = if (useBestModel) "best" else "standard",
        )

        val responseBody = TranslationHttpClient.client.post(url("api/translate")) {
            parameter("token", getApiKey().orEmpty())
            contentType(ContentType.Application.Json)
            setBody(TranslationHttpClient.json.encodeToString(KagiTranslationRequest.serializer(), request))
        }.bodyAsText()
        val response = TranslationHttpClient.json.decodeFromString<KagiTranslationResponse>(responseBody)

        val definitions = response.definition?.let { def ->
            buildList {
                def.primaryMeaning?.let { primary ->
                    add(
                        Definition(
                            type = primary.partOfSpeech.joinToString(", "),
                            definition = primary.definition,
                            example = def.examples.firstOrNull(),
                        ),
                    )
                }
                def.secondaryMeanings.forEach { secondary ->
                    add(
                        Definition(
                            type = secondary.partOfSpeech.joinToString(", "),
                            definition = secondary.definition,
                            example = def.examples.firstOrNull(),
                        ),
                    )
                }
            }.takeIf { it.isNotEmpty() }
        }

        return Translation(
            translatedText = response.translation,
            detectedLanguage = response.detectedLanguage?.iso,
            definitions = definitions,
        )
    }
}