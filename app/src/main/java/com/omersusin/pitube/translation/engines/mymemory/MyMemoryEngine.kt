package com.omersusin.pitube.translation.engines.mymemory

import com.omersusin.pitube.translation.ApiKeyState
import com.omersusin.pitube.translation.CommonLanguages
import com.omersusin.pitube.translation.EngineSettingsProvider
import com.omersusin.pitube.translation.Language
import com.omersusin.pitube.translation.Translation
import com.omersusin.pitube.translation.TranslationEngine
import com.omersusin.pitube.translation.TranslationHttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable

@Serializable
data class MMTranslationResponse(
    val responseData: MMResponseData? = null,
    val responseStatus: Int? = null,
    val responseDetails: String? = null,
)

@Serializable
data class MMResponseData(
    val translatedText: String? = null,
    val detectedLanguage: String? = null,
)

/**
 * MyMemory - free translation memory with an optional API key. Language list
 * is hardcoded upstream; we reuse the device-built common list instead.
 * Ported from Translate You's MMEngine (GPL-3.0).
 */
class MyMemoryEngine(settingsProvider: EngineSettingsProvider) : TranslationEngine(settingsProvider) {

    override val name: String = "MyMemory"

    override val defaultUrl: String = "https://api.mymemory.translated.net"

    override val urlModifiable: Boolean = false

    override val apiKeyState: ApiKeyState = ApiKeyState.OPTIONAL

    override val autoLanguageCode: String? = "Autodetect"

    override suspend fun getLanguages(): List<Language> = CommonLanguages.languages

    /**
     * MyMemory rejects any query over 500 characters (HTTP 400 /
     * "QUERY LENGTH LIMIT EXCEEDED"), so long inputs are packed into
     * newline-delimited chunks of complete lines under the cap and each
     * chunk is translated separately; results re-join line-for-line.
     */
    private fun splitQuery(query: String): List<String> {
        if (query.length <= 500) return listOf(query)
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (line in query.split('\n')) {
            // A single line longer than the cap cannot be packed safely —
            // send it alone as its own chunk and let the provider decide.
            if (line.length > 500) {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString())
                    current.clear()
                }
                chunks.add(line)
                continue
            }
            if (current.isEmpty()) {
                current.append(line)
            } else if (current.length + 1 + line.length <= 500) {
                current.append('\n').append(line)
            } else {
                chunks.add(current.toString())
                current.clear().append(line)
            }
        }
        if (current.isNotEmpty()) chunks.add(current.toString())
        return chunks
    }

    override suspend fun translate(query: String, source: String, target: String): Translation =
        splitQuery(query).map { chunk -> translateChunk(chunk, source, target) }.reduce { acc, next ->
            Translation(
                translatedText = "${acc.translatedText}\n${next.translatedText}",
                detectedLanguage = next.detectedLanguage ?: acc.detectedLanguage,
            )
        }

    private suspend fun translateChunk(query: String, source: String, target: String): Translation {
        val body = TranslationHttpClient.client.get(url("get")) {
            parameter("q", query)
            parameter("langpair", "${sourceOrAuto(source)}|$target")
            parameter("key", getApiKey()?.ifBlank { null })
        }.bodyAsText()

        val response = TranslationHttpClient.json.decodeFromString<MMTranslationResponse>(body)
        if (response.responseStatus != null && response.responseStatus >= 400) {
            throw IllegalStateException(
                response.responseDetails?.takeIf { it.isNotBlank() }
                    ?: "MyMemory returned HTTP ${response.responseStatus}",
            )
        }
        val translatedText = response.responseData?.translatedText.orEmpty()
        if (translatedText.isBlank()) {
            throw IllegalStateException("MyMemory answered without a translation")
        }
        return Translation(
            translatedText = translatedText,
            detectedLanguage = response.responseData?.detectedLanguage,
        )
    }
}