package com.omersusin.pitube.translation.engines.llm

import com.omersusin.pitube.translation.CommonLanguages
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Thrown with a status code when a provider answers with a non-2xx response. */
internal class LlmStatusException(
    val status: HttpStatusCode,
    val serverMessage: String?,
) : Exception(buildString {
    append("Provider returned HTTP ${status.value}")
    if (!serverMessage.isNullOrBlank()) append(": $serverMessage")
})

/** A maximum number of retries was exhausted. */
internal class LlmRetriesExhaustedException(
    message: String,
    cause: Throwable?,
) : Exception(message, cause)

internal val llmJson = Json { ignoreUnknownKeys = true }

/**
 * Shared prompting / parsing for every LLM-backed provider. Two wire modes:
 * - single-line text  -> JSON object  {"translation": "..."}
 * - multi-line text   -> JSON array   ["line1", "line2", ...]  (ViVi's
 *   line-preserving scheme, so paragraphs and line breaks survive).
 */
internal object LlmPrompts {

    const val SYSTEM_OBJECT =
        """You are a precise translation engine. Your output must ALWAYS be a valid """ +
            """JSON object of the form {"translation": "..."}.
CRITICAL RULES:
1. Output ONLY that JSON object.
2. NO explanations, NO questions, NO additional text.
3. Translate the entire input text faithfully, preserving its meaning and tone."""

    private const val SYSTEM_ARRAY =
        """You are a precise translation engine. Your output must ALWAYS be a valid JSON array of strings.
CRITICAL RULES:
1. Output ONLY a JSON array: ["line1", "line2", "line3"]
2. NO explanations, NO questions, NO additional text
3. Each input line maps to exactly one output line
4. Preserve empty lines as empty strings ""
5. Return EXACTLY %d items in the array
6. If uncertain, provide the best translation while maintaining the line count"""

    fun systemFor(lineCount: Int): String =
        if (lineCount > 1) SYSTEM_ARRAY.format(lineCount) else SYSTEM_OBJECT

    fun userSingleLine(text: String, targetName: String): String =
        """Translate the following text into $targetName.
Input:
$text

Output MUST be a JSON object of the form {"translation": "..."} with the FULL translated text."""

    fun userMultiLine(text: String, targetName: String, lineCount: Int): String =
        """Translate the following text into $targetName. Keep the number of paragraphs and line breaks identical.
Input ($lineCount lines):
$text

Output MUST be a JSON array with EXACTLY $lineCount strings."""

    /** Slice a raw model answer down to the JSON payload inside it. */
    fun extractJsonContent(raw: String): String {
        var content = raw.trim()
        if (content.startsWith("```")) {
            val firstNewline = content.indexOf('\n')
            if (firstNewline in 0 until content.length) {
                content = content.substring(firstNewline + 1)
                    .removeSuffix("```")
                    .trim()
            }
        }
        val start = content.indexOfFirst { it == '[' || it == '{' }
        val end = content.indexOfLast { it == ']' || it == '}' }
        if (start >= 0 && end > start) {
            val candidate = content.substring(start, end + 1)
            if (candidate.startsWith('[') || candidate.startsWith('{')) return candidate
        }
        return content
    }

    /** Parse a line-wise answer into exactly [expected] lines (trim or pad with ""). */
    fun parseLineTranslation(content: String, expected: Int): List<String> {
        val lines = parseStringArray(content)
        if (lines.size == expected) return lines
        val normalized = lines.take(expected)
        if (normalized.size < expected) {
            return normalized + List(expected - normalized.size) { "" }
        }
        return normalized
    }

    /** Parse an object-mode answer; falls back to treating the content as raw text. */
    fun parseObjectTranslation(content: String): String {
        val element = runCatching { llmJson.parseToJsonElement(content) }
        if (element.isSuccess) {
            val obj = element.getOrNull() as? JsonObject
            val translation = obj?.get("translation")?.jsonPrimitive?.contentOrNull
            if (!translation.isNullOrBlank()) return translation
        }
        val group = Regex("\"translation\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
            .find(content)
        group?.groupValues?.getOrNull(1)?.let {
            if (it.isNotBlank()) return it
        }
        return content
    }

    /** Robustly read a JSON array of strings, with manual-parse fallbacks. */
    private fun parseStringArray(content: String): List<String> {
        runCatching {
            val element = llmJson.parseToJsonElement(content)
            if (element is JsonArray) {
                return element.mapNotNull { item ->
                    (item as? JsonPrimitive)?.contentOrNull
                }
            }
        }
        val escaped = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
            .findAll(content)
            .map { it.groupValues[1] }
            .toList()
        if (escaped.isNotEmpty()) return escaped
        return if (content.isNotBlank()) listOf(content) else emptyList()
    }

    /** A human-friendly provider error message, when the body exposes one. */
    fun extractServerError(body: String): String? {
        runCatching {
            val obj = llmJson.parseToJsonElement(body).jsonObject
            obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull?.let {
                if (it.isNotBlank()) return it
            }
            obj["message"]?.jsonPrimitive?.contentOrNull?.let {
                if (it.isNotBlank()) return it
            }
        }
        return null
    }
}

/**
 * Run [block] up to [attempts] times with linear backoff. Retries connection
 * failures and server errors (>= 500) plus HTTP 429 (rate limit); surfaces
 * everything else immediately. Throws the last failure when exhausted.
 */
internal suspend fun <T> withProviderRetries(
    attempts: Int = 3,
    block: suspend () -> T,
): T {
    var lastFailure: Exception? = null
    for (attempt in 0 until attempts) {
        try {
            return block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            lastFailure = e
            val retryable = when (e) {
                is LlmStatusException -> e.status.value >= 500 || e.status.value == 429
                else -> true
            }
            if (!retryable) break
            if (attempt == attempts - 1) break
            delay(1000L * (attempt + 1))
        }
    }
    throw LlmRetriesExhaustedException(
        lastFailure?.message ?: "Provider did not answer",
        lastFailure,
    )
}

internal fun languageDisplayName(code: String): String = CommonLanguages.displayNameOf(code)

internal fun maxTokensFor(text: String): Int =
    ((text.length / 4) + 256).coerceIn(256, 8192)