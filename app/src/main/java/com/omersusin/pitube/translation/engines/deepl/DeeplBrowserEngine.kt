package com.omersusin.pitube.translation.engines.deepl

import com.omersusin.pitube.translation.ApiKeyState
import com.omersusin.pitube.translation.EngineSettingsProvider
import com.omersusin.pitube.translation.Language
import com.omersusin.pitube.translation.Translation
import com.omersusin.pitube.translation.TranslationEngine
import com.omersusin.pitube.translation.TranslationHttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeeplWebTranslationRequest(
    val jsonrpc: String,
    val method: String,
    val params: DeeplWebTranslationRequestParams,
    val id: Int,
)

@Serializable
data class DeeplWebTranslationRequestParams(
    val texts: List<DeeplWebTranslationRequestParamsText>,
    val splitting: String,
    val lang: DeeplWebTranslationRequestParamsLang,
    val commonJobParams: Map<String, String>,
    val timestamp: Long,
)

@Serializable
data class DeeplWebTranslationRequestParamsText(
    val text: String,
)

@Serializable
data class DeeplWebTranslationRequestParamsLang(
    @SerialName("target_lang") val targetLang: String,
    @SerialName("source_lang_user_selected") val sourceLangUserSelected: String,
    val preference: DeeplWebTranslationRequestParamsLangPreference,
)

@Serializable
data class DeeplWebTranslationRequestParamsLangPreference(
    val weight: Map<String, String>,
)

@Serializable
data class DeeplWebTranslationResponse(
    val result: DeeplWebTranslationResult = DeeplWebTranslationResult(),
)

@Serializable
data class DeeplWebTranslationResult(
    val texts: List<DeeplWebTranslationTranslation> = listOf(),
    val lang: String = "",
)

@Serializable
data class DeeplWebTranslationTranslation(
    val text: String = "",
)

/**
 * DeepL translator, emulating the browser extension's web endpoint. Unlike
 * DeepL (API) this one needs no key and mirrors exactly what Translate You
 * does: jsonrpc POST with a random id that doubles as the spacing seed for
 * the "method" key. The space-stripping trick is required by the server
 * (otherwise soft-ban). Ported from Translate You's DeeplBrowserEngine
 * (GPL-3.0).
 */
class DeeplBrowserEngine(settingsProvider: EngineSettingsProvider) : TranslationEngine(settingsProvider) {

    override val name: String = "DeepL (Browser)"

    override val defaultUrl: String = "https://www2.deepl.com"

    override val urlModifiable: Boolean = false

    override val apiKeyState: ApiKeyState = ApiKeyState.DISABLED

    override val autoLanguageCode: String? = "auto"

    override suspend fun getLanguages(): List<Language> =
        listOf(
            // List is on https://www.deepl.com/translator
            // Language code can be checked in URL after making a first translation
            Language("ar", "Arabic"),
            Language("bg", "Bulgarian"),
            Language("zh", "Chinese"),
            Language("cs", "Czech"),
            Language("da", "Danish"),
            Language("nl", "Dutch"),
            Language("en", "English"),
            Language("et", "Estonian"),
            Language("fi", "Finnish"),
            Language("fr", "French"),
            Language("de", "German"),
            Language("el", "Greek"),
            Language("hu", "Hungarian"),
            Language("id", "Indonesian"),
            Language("it", "Italian"),
            Language("ja", "Japanese"),
            Language("ko", "Korean"),
            Language("lv", "Latvian"),
            Language("lt", "Lithuanian"),
            Language("nb", "Norwegian (bokmål)"),
            Language("pl", "Polish"),
            Language("pt", "Portuguese"),
            Language("ro", "Romanian"),
            Language("ru", "Russian"),
            Language("sk", "Slovak"),
            Language("sl", "Slovenian"),
            Language("es", "Spanish"),
            Language("sv", "Swedish"),
            Language("tr", "Turkish"),
            Language("uk", "Ukrainian"),
        )

    override suspend fun translate(query: String, source: String, target: String): Translation {
        val id = (floor(Math.random().times(99999)) + 100000).roundToInt().times(1000)
        val body = TranslationHttpClient.json.encodeToString(
            DeeplWebTranslationRequest(
                jsonrpc = "2.0",
                method = "LMT_handle_texts",
                params = DeeplWebTranslationRequestParams(
                    texts = listOf(DeeplWebTranslationRequestParamsText(text = query)),
                    splitting = "newlines",
                    lang = DeeplWebTranslationRequestParamsLang(
                        targetLang = target.uppercase(),
                        sourceLangUserSelected = sourceOrAuto(source.uppercase()),
                        preference = DeeplWebTranslationRequestParamsLangPreference(
                            weight = emptyMap(),
                        ),
                    ),
                    commonJobParams = emptyMap(),
                    timestamp = System.currentTimeMillis(),
                ),
                id = id,
            ),
        ).replace(
            "\"method\":\"",
            // The random ID determines the spacing to use, do NOT change it
            // This is how the client side of the web service works and the server-side
            // expects the same, otherwise you will get soft-banned
            if ((id + 3) % 13 == 0 || (id + 5) % 29 == 0) {
                "\"method\" : \""
            } else "\"method\": \"",
        )
        val webResponse = TranslationHttpClient.json.decodeFromString<DeeplWebTranslationResponse>(
            TranslationHttpClient.client.post(url("jsonrpc")) {
                parameter("client", "chrome-extension,$WEB_CHROME_EXTENSION_VER")
                header("Accept", "*/*")
                header("Accept-Language", "en-US,en;q=0.5")
                header("Authorization", "None")
                header("Origin", "chrome-extension://cofdbpoegempjloogbagkncekinflcnj")
                header("referer", "https://www.deepl.com/")
                header("Sec-Fetch-Dest", "empty")
                header("Sec-Fetch-Mode", "cors")
                header("Sec-Fetch-Site", "none")
                header(
                    "User-Agent",
                    "DeepLBrowserExtension/$WEB_CHROME_EXTENSION_VER $WEB_CHROME_USER_AGENT",
                )
                contentType(ContentType.Application.Json)
                setBody(body)
            }.bodyAsText(),
        )

        return Translation(
            translatedText = webResponse.result.texts.firstOrNull()?.text ?: "",
            detectedLanguage = webResponse.result.lang.lowercase(),
        )
    }

    companion object {
        const val WEB_CHROME_EXTENSION_VER = "1.49.0"
        const val WEB_CHROME_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"
    }
}