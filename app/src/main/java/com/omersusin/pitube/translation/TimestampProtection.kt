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

    // Engines occasionally mangle a placeholder (dropped/extra @, injected
    // spaces, renumbering) so the exact-string restore misses it and the raw
    // token leaks into the rendered line as stray symbols. These patterns
    // catch both recognizable TS-token variants and any residual @@...@@ shape.
    private val leftoverTsTokenRegex = Regex("""@{1,2}\s*TS\s*\d{0,4}\s*@{1,2}""")
    private val residualTokenShapeRegex = Regex("""@@[^@\n]{0,32}@@""")
    private val leftoverTokensRegex = Regex("${leftoverTsTokenRegex.pattern}|${residualTokenShapeRegex.pattern}")

    /** True when [text] still contains an un-restored placeholder shape. */
    fun hasLeftoverTokens(text: String): Boolean = leftoverTokensRegex.containsMatchIn(text)

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
        var result = maskedResult
        tokens.forEachIndexed { index, original ->
            result = result.replace("@@TS$index@@", original)
        }
        // An engine that altered a token made the exact replace above miss it;
        // strip whatever placeholder residue is left instead of rendering it.
        return result.replace(leftoverTokensRegex, " ").replace(Regex("""\s{2,}"""), " ")
    }
}

internal data class MaskedText(val text: String, val tokens: List<String>)
