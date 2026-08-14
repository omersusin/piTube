package com.omersusin.pitube.translation

/**
 * YouTube-style timestamps (e.g. `0:00`, `12:34`, `1:02:03`) must survive
 * translation untouched - surfaces render them as clickable chapter links and
 * a localized timestamp would break tap-to-seek. Before a provider sees the
 * text every match is swapped for a unique placeholder token (`@@TS0@@`,
 * `@@TS1@@`, ...) that translators leave alone, then the original timestamps
 * are restored from the result. Applied in [TranslationController.translate],
 * so every translating surface (titles, descriptions, comments, captions)
 * benefits.
 */
internal object TimestampProtection {

    /** Same pattern the description sheet uses to render clickable timestamps. */
    private val timestampRegex = Regex("""\b(?:[0-9]{1,2}:)?[0-9]{1,2}:[0-9]{2}\b""")

    /** The masked text plus the original tokens, in match order. */
    fun mask(text: String): MaskedText {
        if (text.isEmpty()) return MaskedText(text, emptyList())
        val tokens = mutableListOf<String>()
        val masked = timestampRegex.replace(text) {
            val token = "@@TS${tokens.size}@@"
            tokens.add(it.value)
            token
        }
        return MaskedText(masked, tokens)
    }

    /** Swap the placeholder tokens back to the original timestamp strings. */
    fun restore(maskedResult: String, tokens: List<String>): String {
        if (tokens.isEmpty()) return maskedResult
        var result = maskedResult
        tokens.forEachIndexed { index, original ->
            result = result.replace("@@TS$index@@", original)
        }
        return result
    }
}

internal data class MaskedText(val text: String, val tokens: List<String>)
