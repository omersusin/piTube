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
private data class ChatCompletionChoice(val message: ChatCompletionMessage? = null)

@Serializable
private data class ChatCompletionMessage(val content: String? = null)

@Serializable
private data class ChatCompletionResponse(val choices: List<ChatCompletionChoice> = emptyList())

/**
 * Generic OpenAI-compatible chat-completions engine, ported from ViVi Music's
 * OpenRouterService (GPL-3.0) onto Ktor. Every modern provider family in this
 * app (OpenRouter, OpenAI, Perplexity, Gemini, XAi, Mistral, Custom) speaks
 * this one wire format; each family is a small subclass carrying its own
 * default endpoint and default model list.
 */
abstract class OpenAiCompatibleEngine(settingsProvider: EngineSettingsProvider) :
    TranslationEngine(settingsProvider) {

    override val urlModifiable: Boolean = false

    override val apiKeyState: ApiKeyState = ApiKeyState.REQUIRED

    override val autoLanguageCode: String? = null

    override suspend fun getLanguages(): List<Language> = CommonLanguages.languages

    override suspend fun translate(query: String, source: String, target: String): Translation {
        val apiKey = getApiKey().orEmpty().trim().ifEmpty {
            throw IllegalStateException("An API key is required for $name. Add it in Translation settings.")
        }
        val model = effectiveModel().orEmpty().trim()
        if (model.isBlank() && name != "Custom") {
            throw IllegalStateException("No model selected for $name. Pick one in Translation settings.")
        }
        val url = getUrl().ifBlank {
            throw IllegalStateException("No endpoint configured for the Custom provider. Set a base URL in Translation settings.")
        }

        val lines = query.split('\n')
        val single = lines.size <= 1
        val targetName = languageDisplayName(target).ifBlank { target }

        val body = buildJsonObject {
            if (model.isNotBlank()) put("model", model)
            put("messages", buildJsonArray {
                addJsonObject {
                    put("role", "system")
                    put("content", LlmPrompts.systemFor(if (single) 1 else lines.size))
                }
                addJsonObject {
                    put("role", "user")
                    put("content", if (single) {
                        LlmPrompts.userSingleLine(query, targetName)
                    } else {
                        LlmPrompts.userMultiLine(query, targetName, lines.size)
                    })
                }
            })
            put("temperature", 0.3)
            put("max_tokens", maxTokensFor(query))
        }

        val content = withProviderRetries {
            val response = TranslationHttpClient.client.post(url) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
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
                llmJson.decodeFromString<ChatCompletionResponse>(bodyText)
            }.getOrNull()
            parsed?.choices?.firstOrNull()?.message?.content
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

class OpenRouterEngine(settingsProvider: EngineSettingsProvider) : OpenAiCompatibleEngine(settingsProvider) {
    override val name: String = "OpenRouter"
    override val defaultUrl: String = "https://openrouter.ai/api/v1/chat/completions"
    override val supportedModels: List<String> = listOf(
        "google/gemini-2.5-flash-lite",
        "google/gemini-2.5-flash",
        "x-ai/grok-4-1-fast",
        "deepseek/deepseek-v3.1-terminus:exacto",
        "openai/gpt-4o-mini",
        "google/gemini-3-flash-preview",
    )
}

class OpenAIEngine(settingsProvider: EngineSettingsProvider) : OpenAiCompatibleEngine(settingsProvider) {
    override val name: String = "OpenAI"
    override val defaultUrl: String = "https://api.openai.com/v1/chat/completions"
    override val supportedModels: List<String> = listOf(
        "gpt-4o-mini",
        "gpt-4o",
        "gpt-4-turbo",
        "gpt-4.1-mini",
        "gpt-4.1",
    )
}

class PerplexityEngine(settingsProvider: EngineSettingsProvider) : OpenAiCompatibleEngine(settingsProvider) {
    override val name: String = "Perplexity"
    override val defaultUrl: String = "https://api.perplexity.ai/chat/completions"
    override val supportedModels: List<String> = listOf(
        "sonar",
        "sonar-pro",
        "sonar-reasoning",
    )
}

class GeminiEngine(settingsProvider: EngineSettingsProvider) : OpenAiCompatibleEngine(settingsProvider) {
    override val name: String = "Gemini"
    override val defaultUrl: String =
        "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
    override val supportedModels: List<String> = listOf(
        "gemini-2.5-flash-lite",
        "gemini-2.5-flash",
        "gemini-2.5-pro",
        "gemini-2.0-flash",
    )
}

class XAiEngine(settingsProvider: EngineSettingsProvider) : OpenAiCompatibleEngine(settingsProvider) {
    override val name: String = "XAi"
    override val defaultUrl: String = "https://api.x.ai/v1/chat/completions"
    override val supportedModels: List<String> = listOf(
        "grok-4-1-fast",
        "grok-4",
        "grok-vision-beta",
    )
}

class MistralEngine(settingsProvider: EngineSettingsProvider) : OpenAiCompatibleEngine(settingsProvider) {
    override val name: String = "Mistral"
    override val defaultUrl: String = "https://api.mistral.ai/v1/chat/completions"
    override val supportedModels: List<String> = listOf(
        "mistral-large-latest",
        "mistral-medium-latest",
        "mistral-small-latest",
        "mistral-tiny-latest",
    )
}

class CustomEngine(settingsProvider: EngineSettingsProvider) : OpenAiCompatibleEngine(settingsProvider) {
    override val name: String = "Custom"
    override val defaultUrl: String = ""
    override val urlModifiable: Boolean = true
    override val supportedModels: List<String> = emptyList()
}