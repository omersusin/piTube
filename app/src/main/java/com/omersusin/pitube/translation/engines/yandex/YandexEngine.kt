package com.omersusin.pitube.translation.engines.yandex

import com.omersusin.pitube.translation.ApiKeyState
import com.omersusin.pitube.translation.EngineSettingsProvider
import com.omersusin.pitube.translation.Language
import com.omersusin.pitube.translation.Translation
import com.omersusin.pitube.translation.TranslationEngine
import com.omersusin.pitube.translation.TranslationHttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class YandexResponse(
    val code: Int = 0,
    val lang: String = "",
    val text: List<String> = emptyList(),
)

/**
 * Yandex Translate - the public web endpoint, no key required (the app
 * mimics the Android client). The language table is bundled because the
 * web endpoint does not serve one. Ported from Translate You's
 * YandexEngine (GPL-3.0).
 */
class YandexEngine(settingsProvider: EngineSettingsProvider) : TranslationEngine(settingsProvider) {

    override val name: String = "Yandex"

    override val defaultUrl: String = "https://translate.yandex.net"

    override val urlModifiable: Boolean = false

    override val apiKeyState: ApiKeyState = ApiKeyState.DISABLED

    override val autoLanguageCode: String? = ""

    override val statusNote: String =
        "The web endpoint rotates its session key; translations often fail. Prefer Mozhi or a keyed provider."

    override suspend fun getLanguages(): List<Language> {
        return TranslationHttpClient.json.decodeFromString<Map<String, String>>(
            "{\"af\":\"Afrikaans\",\"sq\":\"Albanian\",\"am\":\"Amharic\",\"ar\":\"Arabic\",\"hy\":\"Armenian\",\"az\":\"Azerbaijani\",\"ba\":\"Bashkir\",\"eu\":\"Basque\",\"be\":\"Belarusian\",\"bn\":\"Bengali\",\"bs\":\"Bosnian\",\"bg\":\"Bulgarian\",\"my\":\"Burmese\",\"ca\":\"Catalan\",\"ceb\":\"Cebuano\",\"zh\":\"Chinese\",\"cv\":\"Chuvash\",\"hr\":\"Croatian\",\"cs\":\"Czech\",\"da\":\"Danish\",\"nl\":\"Dutch\",\"sjn\":\"Elvish (Sindarin)\",\"emj\":\"Emoji\",\"en\":\"English\",\"eo\":\"Esperanto\",\"et\":\"Estonian\",\"fi\":\"Finnish\",\"fr\":\"French\",\"gl\":\"Galician\",\"ka\":\"Georgian\",\"de\":\"German\",\"el\":\"Greek\",\"gu\":\"Gujarati\",\"ht\":\"Haitian\",\"he\":\"Hebrew\",\"mrj\":\"Hill Mari\",\"hi\":\"Hindi\",\"hu\":\"Hungarian\",\"is\":\"Icelandic\",\"id\":\"Indonesian\",\"ga\":\"Irish\",\"it\":\"Italian\",\"ja\":\"Japanese\",\"jv\":\"Javanese\",\"kn\":\"Kannada\",\"kk\":\"Kazakh\",\"kazlat\":\"Kazakh (Latin)\",\"km\":\"Khmer\",\"kv\":\"Komi\",\"ko\":\"Korean\",\"ky\":\"Kyrgyz\",\"lo\":\"Lao\",\"la\":\"Latin\",\"lv\":\"Latvian\",\"lt\":\"Lithuanian\",\"lb\":\"Luxembourgish\",\"mk\":\"Macedonian\",\"mg\":\"Malagasy\",\"ms\":\"Malay\",\"ml\":\"Malayalam\",\"mt\":\"Maltese\",\"mi\":\"Maori\",\"mr\":\"Marathi\",\"mhr\":\"Mari\",\"mn\":\"Mongolian\",\"ne\":\"Nepali\",\"no\":\"Norwegian\",\"os\":\"Ossetian\",\"pap\":\"Papiamento\",\"fa\":\"Persian\",\"pl\":\"Polish\",\"pt\":\"Portuguese\",\"pt-BR\":\"Portuguese (Brazilian)\",\"pa\":\"Punjabi\",\"ro\":\"Romanian\",\"ru\":\"Russian\",\"gd\":\"Scottish Gaelic\",\"sr\":\"Serbian\",\"sr-Latn\":\"Serbian (Latin)\",\"si\":\"Sinhalese\",\"sk\":\"Slovak\",\"sl\":\"Slovenian\",\"es\":\"Spanish\",\"su\":\"Sundanese\",\"sw\":\"Swahili\",\"sv\":\"Swedish\",\"tl\":\"Tagalog\",\"tg\":\"Tajik\",\"ta\":\"Tamil\",\"tt\":\"Tatar\",\"te\":\"Telugu\",\"th\":\"Thai\",\"tr\":\"Turkish\",\"tyv\":\"Tuvan\",\"udm\":\"Udmurt\",\"uk\":\"Ukrainian\",\"ur\":\"Urdu\",\"uz\":\"Uzbek\",\"uzbcyr\":\"Uzbek (Cyrillic)\",\"vi\":\"Vietnamese\",\"cy\":\"Welsh\",\"xh\":\"Xhosa\",\"sah\":\"Yakut\",\"yi\":\"Yiddish\",\"zu\":\"Zulu\"}",
        ).map { (key, value) -> Language(code = key, name = value) }
    }

    override suspend fun translate(query: String, source: String, target: String): Translation {
        val lang = if (source.isEmpty()) target else "$source-$target"
        val uuid = UUID.randomUUID().toString().replace("-", "") + "-0-0"
        val body = TranslationHttpClient.client.post(url("api/v1/tr.json/translate")) {
            parameter("lang", lang)
            parameter("text", query)
            parameter("srv", "android")
            parameter("sid", uuid)
        }.bodyAsText()
        val response = TranslationHttpClient.json.decodeFromString<YandexResponse>(body)
        if (response.text.isEmpty()) throw Exception("Server didn't provide any translation.")
        return Translation(
            translatedText = response.text.first(),
            detectedLanguage = response.lang.split("-").last(),
        )
    }
}