package com.omersusin.pitube.util

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MatchField(val text: String, val weight: Int = 1)

fun normalizeForSearch(text: String): String =
    text.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")

private fun editDistance(a: String, b: String, limit: Int): Int {
    if (a == b) return 0
    if (abs(a.length - b.length) > limit) return limit + 1
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        var best = current[0]
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = min(
                min(current[j - 1] + 1, previous[j] + 1),
                previous[j - 1] + cost
            )
            best = min(best, current[j])
        }
        if (best > limit) return limit + 1
        val swap = previous
        previous = current
        current = swap
    }
    return previous[b.length]
}

private fun isSubsequence(token: String, word: String): Boolean {
    var t = 0
    for (c in word) {
        if (t < token.length && token[t] == c) t++
    }
    return t == token.length
}

private fun scoreToken(token: String, field: String): Int? {
    if (token.isEmpty()) return null
    val words = field.split(' ').filter { it.isNotEmpty() }
    if (field.startsWith(token)) return 120
    words.forEachIndexed { index, word ->
        if (word.startsWith(token)) return 100 - min(index, 8)
    }
    if (field.contains(token)) return 70
    words.forEach { word ->
        if (token.length >= 3 && isSubsequence(token, word)) {
            return max(30, 55 - (word.length - token.length))
        }
    }
    if (token.length >= 4) {
        val limit = if (token.length <= 5) 1 else 2
        words.forEach { word ->
            if (word.length >= 3 && editDistance(token, word, limit) <= limit) return 35
        }
    }
    return null
}

fun fuzzyScore(query: String, vararg fields: MatchField): Int? {
    val tokens = normalizeForSearch(query).split(' ').filter { it.isNotEmpty() }
    if (tokens.isEmpty() || fields.isEmpty()) return null
    val normalized = fields.map { normalizeForSearch(it.text) to it.weight }
    var total = 0
    for (token in tokens) {
        val best = normalized.mapNotNull { (text, weight) ->
            scoreToken(token, text)?.let { it * weight }
        }.maxOrNull() ?: return null
        total += best
    }
    return total - normalized.first().first.length / 8
}
