package com.omersusin.pitube.translation

/**
 * Whether an engine needs (or accepts) an API key.
 *
 * Ported from Translate You's engine abstraction (GPL-3.0).
 */
enum class ApiKeyState {
    DISABLED,
    OPTIONAL,
    REQUIRED,
}

/**
 * The app-side settings contract every engine reads its configuration from.
 * Configuration is resolved live on every request, so changing a pref in the
 * Translation settings screen is picked up by the next translation without
 * restarting anything.
 *
 * Ported from Translate You's [EngineSettingsProvider] (GPL-3.0).
 */
interface EngineSettingsProvider {
    /**
     * Custom API base url, or null to use the engine's [TranslationEngine.defaultUrl].
     * Only relevant when [TranslationEngine.urlModifiable] is true.
     */
    fun getApiUrl(engine: TranslationEngine): String?

    /**
     * The user-supplied API key, or null when none is stored.
     * Only relevant when [TranslationEngine.apiKeyState] is not [ApiKeyState.DISABLED].
     */
    fun getApiKey(engine: TranslationEngine): String?

    /**
     * The chosen model, or null to fall back to [TranslationEngine.supportedModels]' first entry.
     * Only relevant when [TranslationEngine.supportedModels] is not empty.
     */
    fun getSelectedModel(engine: TranslationEngine): String?
}

/**
 * A single translation provider. The whole app talks to providers through this
 * same abstraction - free web services ("classic" Translate You engines) and
 * paid LLM APIs (ViVi-style OpenAI-compatible families, Claude, DeepL) alike -
 * so the UI layer never has to know which one is active.
 *
 * The name string acts as the engine id everywhere (settings keys, cache keys).
 *
 * Ported from Translate You's [TranslationEngine] (GPL-3.0), re-implemented on
 * Ktor instead of Retrofit.
 */
abstract class TranslationEngine(private val settingsProvider: EngineSettingsProvider) {

    abstract val name: String

    /** Default HTTPS base url. */
    abstract val defaultUrl: String

    /** True when the engine is self-hostable and custom instance URLs make sense. */
    abstract val urlModifiable: Boolean

    abstract val apiKeyState: ApiKeyState

    /**
     * The code the engine understands for "detect the source language",
     * or null to disable automatic source detection.
     */
    abstract val autoLanguageCode: String?

    /** Supported models, in picker order; first entry is the default. */
    open val supportedModels: List<String> = emptyList()

    /**
     * Call once before first use. Engines are effectively stateless in this
     * port (they read settings live and share one HttpClient), so this is a
     * no-op hook kept for API compatibility with the upstream abstraction.
     */
    open fun createOrRecreate(): TranslationEngine = this

    /**
     * Languages the engine can translate into. May perform network calls;
     * callers must guard with runCatching and fall back to the common list.
     */
    abstract suspend fun getLanguages(): List<Language>

    /**
     * Translate [query] from [source] into [target]. May throw on errors.
     * An empty [source] means automatic detection (see [autoLanguageCode]).
     */
    abstract suspend fun translate(query: String, source: String, target: String): Translation

    // ------- helpers against the settings provider -------

    fun getUrl(): String = settingsProvider.getApiUrl(this) ?: defaultUrl

    fun getApiKey(): String? = settingsProvider.getApiKey(this)

    fun getSelectedModel(): String? = settingsProvider.getSelectedModel(this)

    /** The effective model: the user's pick, falling back to the first preset. */
    fun effectiveModel(): String? = getSelectedModel() ?: supportedModels.firstOrNull()

    /**
     * Join a relative path onto [getUrl] with exactly one slash between
     * them, tolerating both a wrapped and a bare base url.
     */
    protected fun url(path: String): String {
        val base = getUrl().trimEnd('/')
        return if (base.isEmpty()) path else "$base/${path.trimStart('/')}"
    }

    protected fun sourceOrAuto(source: String): String =
        source.ifEmpty { autoLanguageCode }.orEmpty()

    override fun equals(other: Any?): Boolean {
        if (other is TranslationEngine) {
            return name == other.name &&
                getUrl() == other.getUrl() &&
                getApiKey() == other.getApiKey()
        }
        return false
    }

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = name
}