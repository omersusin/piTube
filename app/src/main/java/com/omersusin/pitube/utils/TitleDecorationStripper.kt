package com.omersusin.pitube.utils

/**
 * Strips "(Official Video)"-style decorations from video titles and lyric lines.
 *
 * ICU NOTE (verified by a production crash in 2.3.0): Android's ICU regex
 * rejects `\]` inside character classes AND mishandles the hex escape when it
 * appears at the END of a class (`[)\u005d]` → "Missing closing bracket").
 * The ONLY proven-safe shapes are:
 *   - `[^)]*`            — plain negated paren class
 *   - `[^\u005d]*\u005d`  — hex-escaped ] as the FIRST member of a negated
 *                          class, and as a bare literal outside classes
 * Every pattern below sticks to those two forms; bracket alternatives are
 * split into their own alternation arm instead of sharing one mixed class.
 */
object TitleDecorationStripper {

    private val WHITESPACE = Regex("""\s+""")

    private const val KEYWORDS =
        """(?:official|lyrics?|audio|video|hd|4k|mv|visualizer|performance|color\s*coded|eng(?:lish)?|sub(?:titl(?:e|ed))?|romanized?|kanji|hangul|rom)"""

    // Compiled defensively: a PatternSyntaxException here previously crashed
    // the whole player overlay at class-init (ExceptionInInitializerError).
    // Null means "strip nothing" instead of "kill the app".
    private val BRACKETED: Regex? = runCatching {
        Regex("""\([^)]*\)|\[[^\u005d]*\u005d""")
    }.getOrNull()

    /**
     * Keyword-bearing decorations only — safe for lyric lines where brackets can
     * carry real content ("(x2)", "(Live 2019)"). Two alternation arms so each
     * closer uses its proven-safe ICU shape.
     */
    private val DECORATION: Regex? = runCatching {
        Regex(
            """\s*\($KEYWORDS[^)]*\)\s*|\s*\[$KEYWORDS[^\u005d]*\u005d\s*""",
            RegexOption.IGNORE_CASE,
        )
    }.getOrNull()

    /** Repeatedly removes keyword decorations until stable ("[HD] X (Official)" → "X"). */
    fun stripDecorations(text: String): String {
        val decoration = DECORATION ?: return text.trim()
        var out = text
        while (true) {
            val next = out.replace(decoration, " ").replace(WHITESPACE, " ").trim()
            if (next == out) return next
            out = next
        }
    }

    /** Removes every bracketed segment — intended for search query prefill. */
    fun stripAll(text: String): String {
        val bracketed = BRACKETED ?: return text.trim()
        return text.replace(bracketed, " ").replace(WHITESPACE, " ").trim()
    }
}
