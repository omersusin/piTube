package com.omersusin.pitube.translation.engines.llm

import com.omersusin.pitube.translation.ApiKeyState
import com.omersusin.pitube.translation.CommonLanguages
import com.omersusin.pitube.translation.EngineSettingsProvider
import com.omersusin.pitube.translation.Language
import com.omersusin.pitube.translation.Translation
import com.omersusin.pitube.translation.TranslationEngine
import com.omersusin.pitube.translation.TranslationHttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private data class AnthropicContent(val type: String? = null, val text: String? = null)

@Serializable
private data class AnthropicResponse(val content: List<AnthropicContent> = emptyList())

/**
 * Native Anthropic Messages API client for Claude. ViVi routes Claude through
 * its OpenAI-format client into Anthropic's `/v1/messages` endpoint, which
 * the real API rejects; this engine speaks Anthropic's actual wire schema
 * (x-api-key header, system + messages body, content[0].text answer).
 */
class ClaudeEngine(settingsProvider: EngineSettingsProvider) : TranslationEngine(settingsProvider) {

    override val name: String = "Claude"

    override val defaultUrl: String = "https://api.anthropic.com/v1/messages"

    override val urlModifiable: Boolean = false

    override val apiKeyState: ApiKeyState = ApiKeyState.REQUIRED

    override val autoLanguageCode: String? = null

    override val supportedModels: List<String> = listOf(
        "claude-3-5-haiku-latest",
        "claude-3-5-sonnet-latest",
        "claude-3-7-sonnet-latest",
        "claude-sonnet-4-20250514",
        "claude-opus-4-20250514",
    )

    override suspend fun getLanguages(): List<Language> = CommonLanguages.languages

    override suspend fun translate(query: String, source: String, target: String): Translation {
        val apiKey = getApiKey().orEmpty().trim().ifEmpty {
            throw IllegalStateException("An API key is required for Claude. Add it in Translation settings.")
        }
        val model = effectiveModel().orEmpty().trim().ifEmpty {
            throw IllegalStateException("No model selected for Claude. Pick one in Translation settings.")
        }

        val lines = query.split('\n')
        val single = lines.size <= 1
        val targetName = languageDisplayName(target).ifBlank { target }

        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokensFor(query))
            put("temperature", 0.3)
            put("system", LlmPrompts.systemFor(if (single) 1 else lines.size))
            put("messages", buildJsonArray {
                addJsonObject {
                    put("role", "user")
                    put("content", if (single) {
                        LlmPrompts.userSingleLine(query, targetName)
                    } else {
                        LlmPrompts.userMultiLine(query, targetName, lines.size)
                    })
                }
            })
        }

        val content = withProviderRetries {
            val response = TranslationHttpClient.client.post(getUrl()) {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            val bodyText = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw LlmStatusException(
                    response.status,
                    LlmPrompts.extractServerError(bodyText)
                        ?: "Provider returned HTTP ${response.status.value}",
                )
            }
            val parsed = runCatching {
                llmJson.decodeFromString<AnthropicResponse>(bodyText)
            }.getOrNull()
            parsed?.content?.firstOrNull { it.type == "text" }?.text
                ?.takeIf { it.isNotBlank() }
                ?: throw LlmStatusException(
                    response.status,
                    "Provider answered without a translation",
                )
        }

        val result = if (single) {
            LlmPrompts.parseObjectTranslation(LlmPrompts.extractJsonContent(content))
        } else {
            LlmPrompts.parseLineTranslation(
                LlmPrompts.extractJsonContent(content),
                lines.size,
            ).joinToString("\n")
        }
        return Translation(translatedText = result)
    }
}