package com.omersusin.pitube.data.translation

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.omersusin.pitube.translation.ApiKeyState
import com.omersusin.pitube.translation.EngineSettingsProvider
import com.omersusin.pitube.translation.TranslationEngine

/**
 * The per-engine configuration store for the translation feature.
 *
 * Everything engine-specific (API keys, custom instance URLs, selected
 * models) lives in one [EncryptedSharedPreferences] file so secrets never
 * touch plain storage, mirroring the ProfileManager pattern. Values are read
 * synchronously - the [EngineSettingsProvider] contract is synchronous - and
 * empty strings are normalised to null so engines fall back to defaults.
 */
class TranslationEnginePrefs(context: Context) : EngineSettingsProvider {

    private val prefs: SharedPreferences = buildPrefs(context.applicationContext)

    // ---------------- EngineSettingsProvider ----------------

    override fun getApiUrl(engine: TranslationEngine): String? =
        prefs.getString(key(engine, "url"), "")?.ifBlank { null }?.trim()

    override fun getApiKey(engine: TranslationEngine): String? =
        prefs.getString(key(engine, "key"), "")?.ifBlank { null }

    override fun getSelectedModel(engine: TranslationEngine): String? =
        prefs.getString(key(engine, "model"), "")?.ifBlank { null }

    // ---------------- Writes ----------------

    fun setApiUrl(engine: TranslationEngine, url: String) {
        prefs.edit().putString(key(engine, "url"), url.trim()).apply()
    }

    fun setApiKey(engine: TranslationEngine, apiKey: String) {
        prefs.edit().putString(key(engine, "key"), apiKey.trim()).apply()
    }

    fun setSelectedModel(engine: TranslationEngine, model: String) {
        prefs.edit().putString(key(engine, "model"), model.trim()).apply()
    }

    // ---------------- Active provider ----------------

    /** The selected provider name; null means the registry default. */
    var providerName: String?
        get() = prefs.getString(KEY_PROVIDER, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_PROVIDER, value?.trim()).apply()
        }

    // ---------------- Helpers ----------------

    fun hasApiKey(engine: TranslationEngine): Boolean =
        engine.apiKeyState != ApiKeyState.DISABLED && !getApiKey(engine).isNullOrBlank()

    /** The key row summary: masked bullets like ViVi's settings row. */
    fun maskedKey(engine: TranslationEngine): String? =
        getApiKey(engine)?.let { "•".repeat(minOf(it.length, 8)) }

    private fun key(engine: TranslationEngine, suffix: String): String =
        "engine_${engine.name}_$suffix"

    companion object {
        private const val PREFS_FILE_NAME = "translation_settings"
        private const val KEY_PROVIDER = "provider"

        private fun buildPrefs(context: Context) = EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}