package com.omersusin.pitube.utils

/**
 * Strips "(Official Video)"-style decorations from video titles and lyric lines.
 *
 * ICU NOTE: Android's java.util.regex throws PatternSyntaxException for `\]`
 * escapes inside character classes (the crash fixed by commits e4dccae/615c145).
 * Every pattern here therefore expresses the closing bracket as the hex escape
 * [^\u005d]*\u005d instead of a literal backslash-bracket.
 */
object TitleDecorationStripper {

    private val WHITESPACE = Regex("""\s+""")

    /** All parenthesized/bracketed segments — for search-prefill queries. */
    private val BRACKETED = Regex("""\([^)]*\)|\[[^\u005d]*\u005d""")

    /**
     * Keyword-bearing decorations only — safe for lyric lines where brackets can
     * carry real content ("(x2)", "(Live 2019)"). Matched case-insensitively at
     * common decoration vocabulary.
     */
    private val DECORATION = Regex(
        """\s*[([](?:official|lyrics?|audio|video|hd|4k|mv|visualizer|performance""" +
            """|color\s*coded|eng(?:lish)?|sub(?:titl(?:e|ed))?|romanized?|kanji|hangul|rom)[^)\u005d]*[)\u005d]\s*""",
        RegexOption.IGNORE_CASE,
    )

    /** Repeatedly removes keyword decorations until stable ("[HD] X (Official)" → "X"). */
    fun stripDecorations(text: String): String {
        var out = text
        while (true) {
            val next = out.replace(DECORATION, " ").replace(WHITESPACE, " ").trim()
            if (next == out) return next
            out = next
        }
    }

    /** Removes every bracketed segment — intended for search query prefill. */
    fun stripAll(text: String): String =
        text.replace(BRACKETED, " ").replace(WHITESPACE, " ").trim()
}
