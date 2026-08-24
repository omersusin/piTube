package com.omersusin.pitube.data.translation

import com.omersusin.pitube.data.local.dao.TranslationCacheDao
import com.omersusin.pitube.data.local.entity.CachedTranslationEntity
import com.omersusin.pitube.translation.Language
import com.omersusin.pitube.translation.MaskedText
import com.omersusin.pitube.translation.TimestampProtection
import com.omersusin.pitube.translation.TranslationEngine
import com.omersusin.pitube.translation.TranslationEngines
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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

    // App-lifetime scope for coalesced translation requests so dedup serves
    // every caller regardless of which composable (or its lifecycle) spawned it.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Bounds how many provider calls run at once. A feed renders many texts via
    // rememberTranslatedText; without a cap every visible composable would fire
    // its own request the moment the screen composes.
    private val networkPermits = Semaphore(MAX_CONCURRENT_TRANSLATIONS)

    // Collapses in-flight calls for the exact same cache key (same engine +
    // model + url + target + source text) into one provider request so an
    // 80-item card list does not blast 80 identical HTTP calls.
    private val inFlight = ConcurrentHashMap<String, Deferred<String?>>()

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
    suspend fun translate(text: String, targetCode: String? = null): String? =
        translateInternal(text, targetCode)

    /**
     * Strict variant of [translate] for callers that must tell "no
     * translation happened" apart from a successful result. Every
     * same-language early-return ([LanguageScriptUtil.shouldSkip], the
     * engine's own detected-language echo) yields the ORIGINAL text back
     * from [translate]; here that echo is converted to `null` so batch /
     * per-line fallbacks treat it as a failed line instead of silently
     * dropping it as "already translated".
     */
    suspend fun translateOrNull(text: String, targetCode: String? = null): String? {
        val result = translateInternal(text, targetCode)
        if (result == null) {
            Log.d(
                TAG,
                "translateOrNull: provider failure (lastError=${_lastError.value}) -> null " +
                    "[engine=${currentEngine().name} target=${effectiveTarget(targetCode)} text='${text.take(40)}']",
            )
            return null
        }
        if (result.trim().equals(text.trim(), ignoreCase = true)) {
            Log.d(TAG, "translateOrNull: engine ECHOED the original text -> null")
            return null
        }
        return result
    }

    private suspend fun translateInternal(text: String, targetCode: String?): String? {
        if (text.isBlank()) return text
        val target = effectiveTarget(targetCode)
        val engine = currentEngine()

        if (LanguageScriptUtil.shouldSkip(text, target)) return text

        // A cached answer is served synchronously — UNLESS it is a poisoned
        // row (older builds could cache the original text as its own
        // translation, producing permanent 100% echo). A cached value equal
        // to the source is treated as invalid and re-translated live.
        val masked = TimestampProtection.mask(text)
        val cacheId = cacheId(engine, target, text)
        val cached = cacheDao.get(cacheId)
        if (!cached.isNullOrBlank() &&
            !cached.trim().equals(text.trim(), ignoreCase = true)
        ) {
            // Rows written before leftover-token stripping may contain raw
            // "@@TS0@@" residue — re-translate live instead of serving them.
            if (TimestampProtection.hasLeftoverTokens(cached)) {
                Log.w(TAG, "cache row with placeholder residue healed for engine=${engine.name} target=$target")
                cacheDao.delete(cacheId)
            } else {
                return cached
            }
        }
        if (cached != null) {
            Log.w(TAG, "poisoned cache row healed for engine=${engine.name} target=$target")
            cacheDao.delete(cacheId)
        }

        // Collapse identical in-flight calls into one provider request; the
        // first caller starts the network call and every other caller awaits
        // the same result instead of firing its own request.
        inFlight[cacheId]?.let { return it.await() }

        val deferred =
            appScope.async {
                withContext(Dispatchers.IO) {
                    networkPermits.withPermit {
                        try {
                            performTranslation(engine, masked, target, cacheId, text)
                        } catch (e: CancellationException) {
                            throw e
                        }
                    }
                }
            }
        inFlight.putIfAbsent(cacheId, deferred)?.let {
            // Lost the race — someone else's request is already outstanding.
            return it.await()
        }
        // Drop the entry once the deferred settles, whatever its outcome.
        deferred.invokeOnCompletion { inFlight.remove(cacheId, deferred) }
        return deferred.await()
    }

    /**
     * The actual provider round-trip (already gated by the concurrency cap).
     * The Room cache write happens here so the first caller to finish caches
     * a result all coalesced callers observe.
     */
    private suspend fun performTranslation(
        engine: TranslationEngine,
        masked: MaskedText,
        target: String,
        cacheId: String,
        original: String,
    ): String? {
        try {
            val translation = engine.translate(masked.text, "", target)
            if (translation.detectedLanguage != null &&
                translation.detectedLanguage.equals(target, ignoreCase = true)
            ) {
                // The engine itself detected the text as already being in the
                // target language - nothing to translate.
                return original
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
                    sourceText = original,
                    translatedText = translated,
                ),
            )
            maybePrune()
            return translated
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The named/current provider never answered (DNS / refused /
            // timed out — i.e. no bytes were ever received). As a one-shot
            // safety net, try the next ALIVE keyless engine once so a dead
            // instance does not silence translation entirely. Parse errors
            // and HTTP failures are NOT retried: a wrong API shape must stay
            // visible instead of being silently masked by a fallback.
            if (failedBeforeResponse(e)) {
                val alt = TranslationEngines.getAllEngines(enginePrefs)
                    .firstOrNull { it.name != engine.name && it.name in TranslationEngines.defaultKeylessOrder }
                if (alt != null) {
                    Log.w(TAG, "translate via ${engine.name} unreachable — retrying once via ${alt.name}")
                    val retried = try {
                        alt.translate(masked.text, "", target)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    if (retried != null) {
                        val translated = TimestampProtection.restore(retried.translatedText, masked.tokens)
                        if (translated.isNotBlank() &&
                            !translated.trim().equals(original.trim(), ignoreCase = true)
                        ) {
                            cacheDao.insert(
                                CachedTranslationEntity(
                                    id = cacheId,
                                    engine = alt.name,
                                    targetLanguage = target,
                                    sourceText = original,
                                    translatedText = translated,
                                ),
                            )
                            maybePrune()
                            return translated
                        }
                    }
                }
            }
            val msg = friendlyMessage(e, engine)
            _lastError.value = msg
            // Surface the REAL underlying error — friendlyMessage can collapse
            // the cause chain and the settings StateFlow alone made diagnosis
            // impossible on device logs.
            Log.w(TAG, "translate via ${engine.name} failed: ${e.message} (cause=${e.cause?.message})")
            return null
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
            _lastError.value = friendlyMessage(it, engine)
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

    /**
     * A short, human-friendly error message for [e]. Engines may surface
     * kotlinx.serialization parser dumps, low-level IO stack traces or HTTP
     * status text; none of that is useful to the user, so it is collapsed to
     * one of a few canned explanations (never raw parser / socket text).
     */
    /** True when [e] means the request never got a response byte. */
    private fun failedBeforeResponse(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            when (t) {
                is java.net.UnknownHostException,
                is java.net.ConnectException,
                is java.net.SocketTimeoutException,
                is java.io.IOException,
                -> return true
            }
            t = t.cause
        }
        return false
    }

    private fun friendlyMessage(e: Throwable, engine: TranslationEngine): String {
        val name = engine.name
        val type = e.javaClass
        return when {
            // Provider answered something we could not decode, e.g. an error
            // page instead of JSON, or a changed API shape.
            type.let { it.name.contains("Serialization") || it.name.contains("JsonConvert") } ->
                "$name returned an unexpected response. The service may be down or its API changed."

            // Reaching the server failed (DNS / refused / TLS). "Could not resolve host"
            // and "Failed to connect" are common strings inside these IO errors.
            type.let { it.name.contains("UnknownHost") || it.name.contains("ConnectException") } ->
                "Couldn't reach $name. Check your connection or the instance URL."

            type.let { it.name.contains("Timeout") } ->
                "$name is taking too long to respond. Try again."

            // 4xx/5xx surfaced by engines (IllegalStateException with our own text).
            e.message.isNullOrBlank() -> "Translation failed"
            else -> e.message.orEmpty()
        }
    }

    companion object {
        private const val TAG = "TranslationController"

        /** Provider calls run concurrently up to this many. */
        const val MAX_CONCURRENT_TRANSLATIONS = 4

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