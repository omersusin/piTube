package com.omersusin.pitube.translation.engines.simplytranslate

import com.omersusin.pitube.translation.ApiKeyState
import com.omersusin.pitube.translation.EngineSettingsProvider
import com.omersusin.pitube.translation.Language
import com.omersusin.pitube.translation.Translation
import com.omersusin.pitube.translation.TranslationEngine
import com.omersusin.pitube.translation.TranslationHttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class STTranslationResponse(
    @SerialName("definitions") val definitions: STDefinition? = null,
    @SerialName("pronunciation") val pronunciation: String? = null,
    @SerialName("source_language") val sourceLanguage: String? = null,
    @SerialName("translated_text") val translatedText: String = "",
)

@Serializable
data class STDefinition(
    val abbreviation: List<STAbbreviation> = listOf(),
)

@Serializable
data class STAbbreviation(
    val definition: String? = null,
)

/**
 * SimplyTranslate - free multi-backend gateway (Google/LibreTranslate/Reverso/
 * iciba via the model selector), self-hostable. Ported from Translate You's
 * STEngine (GPL-3.0); audio is omitted since this app has no audio playback.
 */
class SimplyTranslateEngine(settingsProvider: EngineSettingsProvider) : TranslationEngine(settingsProvider) {

    override val name: String = "SimplyTranslate"

    override val defaultUrl: String = "https://simplytranslate.org/"

    override val urlModifiable: Boolean = true

    override val apiKeyState: ApiKeyState = ApiKeyState.DISABLED

    override val autoLanguageCode: String? = "auto"

    override val supportedModels: List<String> = listOf("google", "libre", "reverso", "iciba")

    override suspend fun getLanguages(): List<Language> {
        val body = TranslationHttpClient.client.get(url("api/target_languages/")) {
            parameter("engine", getSelectedModel())
        }.bodyAsText()
        return TranslationHttpClient.json.decodeFromString<Map<String, String>>(body).map { (code, name) ->
            Language(code = code, name = name)
        }
    }

    override suspend fun translate(query: String, source: String, target: String): Translation {
        val body = TranslationHttpClient.client.get(url("api/translate/")) {
            parameter("engine", getSelectedModel())
            parameter("from", sourceOrAuto(source))
            parameter("to", target)
            parameter("text", query)
        }.bodyAsText()
        val response = TranslationHttpClient.json.decodeFromString<STTranslationResponse>(body)
        return Translation(
            translatedText = response.translatedText,
            detectedLanguage = response.sourceLanguage,
            transliterations = listOfNotNull(response.pronunciation?.takeIf { it.isNotBlank() }),
        )
    }
}