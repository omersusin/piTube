package com.omersusin.pitube.data.translation

import java.util.Locale

/**
 * Cheap heuristic for "this text is probably already in the target language".
 *
 * Script-level skip is only claimed for language pairs whose scripts are
 * distinctive enough that script equality means language equality in practice
 * (Cyrillic, Han, Hangul, Arabic, ...). Latin-script targets (en, fr, de, ...)
 * never skip on script, because English and French share the Latin script.
 *
 * Engines that echo a detected source language (when the source is "auto")
 * give a stronger signal; the controller trusts that first when present.
 */
object LanguageScriptUtil {

    private val targetScripts: Map<String, Set<Character.UnicodeScript>> = mapOf(
        // CJK is deliberately split so zh<->ja don't false-skip (Japanese is
        // dominated by kana, Chinese by han; kanji-heavy Japanese may still
        // get one extra API call - acceptable).
        "zh" to setOf(Character.UnicodeScript.HAN),
        "zh-cn" to setOf(Character.UnicodeScript.HAN),
        "zh-hans" to setOf(Character.UnicodeScript.HAN),
        "zh-tw" to setOf(Character.UnicodeScript.HAN),
        "zh-hant" to setOf(Character.UnicodeScript.HAN),
        "ja" to setOf(
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
        ),
        "ko" to setOf(Character.UnicodeScript.HANGUL),
        "ru" to setOf(Character.UnicodeScript.CYRILLIC),
        "uk" to setOf(Character.UnicodeScript.CYRILLIC),
        "be" to setOf(Character.UnicodeScript.CYRILLIC),
        "bg" to setOf(Character.UnicodeScript.CYRILLIC),
        "mk" to setOf(Character.UnicodeScript.CYRILLIC),
        "sr" to setOf(Character.UnicodeScript.CYRILLIC),
        "ar" to setOf(Character.UnicodeScript.ARABIC),
        "fa" to setOf(Character.UnicodeScript.ARABIC),
        "ur" to setOf(Character.UnicodeScript.ARABIC),
        "he" to setOf(Character.UnicodeScript.HEBREW),
        "yi" to setOf(Character.UnicodeScript.HEBREW),
        "el" to setOf(Character.UnicodeScript.GREEK),
        "hi" to setOf(Character.UnicodeScript.DEVANAGARI),
        "ne" to setOf(Character.UnicodeScript.DEVANAGARI),
        "mr" to setOf(Character.UnicodeScript.DEVANAGARI),
        "th" to setOf(Character.UnicodeScript.THAI),
        "ta" to setOf(Character.UnicodeScript.TAMIL),
        "te" to setOf(Character.UnicodeScript.TELUGU),
        "ml" to setOf(Character.UnicodeScript.MALAYALAM),
        "kn" to setOf(Character.UnicodeScript.KANNADA),
        "bn" to setOf(Character.UnicodeScript.BENGALI),
        "gu" to setOf(Character.UnicodeScript.GUJARATI),
        "pa" to setOf(Character.UnicodeScript.GURMUKHI),
        "am" to setOf(Character.UnicodeScript.ETHIOPIC),
        "km" to setOf(Character.UnicodeScript.KHMER),
        "lo" to setOf(Character.UnicodeScript.LAO),
        "my" to setOf(Character.UnicodeScript.MYANMAR),
        "ka" to setOf(Character.UnicodeScript.GEORGIAN),
        "hy" to setOf(Character.UnicodeScript.ARMENIAN),
        "si" to setOf(Character.UnicodeScript.SINHALA),
    )

    /**
     * True when [text] is very likely already written in the [targetCode]
     * language's script and translation would be pointless.
     */
    fun shouldSkip(text: String, targetCode: String): Boolean {
        if (text.isBlank()) return true
        if (text.length < 4) return false
        val expected = targetScripts[targetCode.lowercase(Locale.ROOT)] ?: return false
        val dominant = dominantScript(text) ?: return false
        return dominant in expected
    }

    /** The Unicode script that covers most of [text], or null when ambiguous. */
    fun dominantScript(text: String): Character.UnicodeScript? {
        val counts = HashMap<Character.UnicodeScript, Int>()
        var scored = 0
        for (ch in text) {
            if (!ch.isLetter()) continue
            val script = Character.UnicodeScript.of(ch.code)
            if (script == Character.UnicodeScript.COMMON ||
                script == Character.UnicodeScript.INHERITED
            ) {
                continue
            }
            counts[script] = (counts[script] ?: 0) + 1
            scored++
        }
        if (scored < 4) return null
        return counts.maxByOrNull { it.value }?.key
    }
}