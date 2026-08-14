package com.omersusin.pitube.translation

import java.util.Locale

/**
 * A device-built list of ISO 639-1 language codes with display names in the
 * device locale. Used as the fallback language list for engines that cannot
 * (or do not bother to) fetch their own language table, and as the union
 * source for the target-language picker.
 */
object CommonLanguages {

    /** Extra codes not necessarily present in the device locale table. */
    private val extras: List<Pair<String, String>> = listOf(
        "auto" to "Auto",
        "mn" to "Mongolian",
        "ne" to "Nepali",
        "si" to "Sinhala",
        "km" to "Khmer",
        "my" to "Burmese",
        "lo" to "Lao",
        "am" to "Amharic",
        "sw" to "Swahili",
        "zu" to "Zulu",
        "af" to "Afrikaans",
        "sq" to "Albanian",
        "bs" to "Bosnian",
        "ca" to "Catalan",
        "cy" to "Welsh",
        "eo" to "Esperanto",
        "et" to "Estonian",
        "eu" to "Basque",
        "ga" to "Irish",
        "gl" to "Galician",
        "is" to "Icelandic",
        "ka" to "Georgian",
        "kk" to "Kazakh",
        "lt" to "Lithuanian",
        "lv" to "Latvian",
        "mk" to "Macedonian",
        "mt" to "Maltese",
        "sl" to "Slovenian",
        "sr" to "Serbian",
        "lb" to "Luxembourgish",
    )

    val languages: List<Language> by lazy {
        val fromDevice = Locale.getAvailableLocales()
            .mapNotNull { locale ->
                val code = locale.language
                if (code.isBlank() || code == "und") return@mapNotNull null
                val name = runCatching { locale.displayLanguage }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                code to name
            }
            .distinctBy { it.first }
            .toMutableList()

        extras.forEach { (code, name) ->
            val existing = fromDevice.indexOfFirst { it.first == code }
            if (existing >= 0) {
                if (fromDevice[existing].second == null) fromDevice[existing] = code to name
            } else {
                fromDevice.add(code to name)
            }
        }

        fromDevice
            .filter { it.second != null }
            .map { Language(it.first, it.second!!) }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    fun byCode(code: String): Language? =
        languages.firstOrNull { it.code.equals(code, ignoreCase = true) }

    /** "en" -> "English" using the device locale, falling back to the code. */
    fun displayNameOf(code: String): String {
        if (code.isBlank()) return "Auto"
        byCode(code)?.let { return it.name }
        return runCatching {
            Locale.forLanguageTag(code).displayLanguage
                .takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
        }.getOrNull() ?: code
    }
}