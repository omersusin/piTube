package com.omersusin.pitube.data.translation

import com.omersusin.pitube.data.local.dao.TranslationCacheDao
import com.omersusin.pitube.data.local.entity.CachedTranslationEntity
import com.omersusin.pitube.translation.Language
import com.omersusin.pitube.translation.TimestampProtection
import com.omersusin.pitube.translation.TranslationEngine
import com.omersusin.pitube.translation.TranslationEngines
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single entry point the whole app translates through.
 *
 * Responsibilities: resolve the active engine, apply the "already in target
 * language" heuristics, serve and write the Room translation cache, and turn
 * every failure into a silent `null` (the UI keeps the original text) plus a
 * `lastError` hint for the settings "Test" action.
 */
@Singleton
class TranslationController @Inject constructor(
    private val enginePrefs: TranslationEnginePrefs,
    private val cacheDao: TranslationCacheDao,
) {

    val engines: List<TranslationEngine> = TranslationEngines.getAllEngines(enginePrefs)

    private val _lastError = MutableStateFlow<String?>(null)

    /** Friendly message from the most recent failing call, if any. */
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val languageCache = HashMap<String, List<Language>>()

    private var pruneCounter = 0

    /** The engine the user picked, or the registry default. */
    fun currentEngine(): TranslationEngine =
        TranslationEngines.findByName(enginePrefs.providerName, enginePrefs)

    fun engineByName(name: String?): TranslationEngine =
        TranslationEngines.findByName(name, enginePrefs)

    fun clearError() {
        _lastError.value = null
    }

    /** Target code, or the device locale's language when none is configured. */
    fun effectiveTarget(targetCode: String?): String =
        targetCode?.takeIf { it.isNotBlank() } ?: Locale.getDefault().language

    /**
     * Translate [text] into the given target language (device locale when
     * null). Returns null on any failure so callers fall back to the
     * original; never throws.
     */
    suspend fun translate(text: String, targetCode: String? = null): String? {
        if (text.isBlank()) return text
        val target = effectiveTarget(targetCode)
        val engine = currentEngine()

        if (LanguageScriptUtil.shouldSkip(text, target)) return text

        // YouTube-style timestamps ("0:00", "12:34") must come back untouched
        // so chapter links keep working after translation.
        val masked = TimestampProtection.mask(text)
        val cacheId = cacheId(engine, target, text)
        cacheDao.get(cacheId)?.let { cached ->
            if (cached.isNotBlank()) return cached
            // A blank entry means the provider answered empty under a broken
            // configuration; ignore it so a fixed config can retry.
        }

        return try {
            val translation = engine.translate(masked.text, "", target)
            if (translation.detectedLanguage != null &&
                translation.detectedLanguage.equals(target, ignoreCase = true)
            ) {
                // The engine itself detected the text as already being in the
                // target language - nothing to translate.
                return text
            }
            val translated = TimestampProtection.restore(
                translation.translatedText,
                masked.tokens,
            )
            if (translated.isBlank()) {
                _lastError.value = "${engine.name} answered without a translation"
                return null
            }
            cacheDao.insert(
                CachedTranslationEntity(
                    id = cacheId,
                    engine = engine.name,
                    targetLanguage = target,
                    sourceText = text,
                    translatedText = translated,
                ),
            )
            maybePrune()
            translated
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _lastError.value = e.message ?: "Translation failed"
            null
        }
    }

    /**
     * Run a real provider call, bypassing cache and heuristics, to validate
     * the configured key + model + endpoint (the settings "Test" action).
     */
    suspend fun testConnection(targetCode: String? = null): Result<String> {
        val engine = currentEngine()
        val target = effectiveTarget(targetCode)
        return runCatching {
            engine.translate(
                "Hello, world! How are you today?",
                "",
                target,
            ).translatedText
        }.onFailure {
            if (it is CancellationException) throw it
            _lastError.value = it.message ?: "Translation failed"
        }
    }

    /**
     * The language list for the target-language picker: the engine's own
     * table when it can produce one (cached per engine), else the
     * device-built common list.
     */
    suspend fun languagesFor(engine: TranslationEngine): List<Language> {
        languageCache[engine.name]?.let { return it }
        val languages = runCatching { engine.getLanguages() }
            .getOrElse { emptyList() }
            .takeIf { it.isNotEmpty() }
            ?: com.omersusin.pitube.translation.CommonLanguages.languages
        languageCache[engine.name] = languages
        return languages
    }

    private suspend fun maybePrune() {
        pruneCounter++
        if (pruneCounter % 20 == 0) cacheDao.prune()
    }

    companion object {
        /**
         * Cache key scoped to the engine's active configuration (name, model
         * and instance url) so switching models or self-hosted instances
         * yields fresh translations instead of stale ones.
         */
        fun cacheId(engine: TranslationEngine, target: String, text: String): String =
            buildString {
                append(engine.name)
                append('|')
                append(engine.effectiveModel().orEmpty())
                append('|')
                append(engine.getUrl())
                append('|')
                append(target)
                append('|')
                append(text.hashCode())
            }
    }
}