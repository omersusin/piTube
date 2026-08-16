package com.omersusin.pitube.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The per-provider credential store for the voice STT feature.
 *
 * Everything provider-specific (API keys, Azure region, IBM instance URL)
 * lives in one [EncryptedSharedPreferences] file so secrets never touch plain
 * storage, mirroring TranslationEnginePrefs/ProfileManager. Values are read
 * synchronously and empty strings are normalised to null.
 */
class SttApiKeyStore(context: Context) {

    private val prefs: SharedPreferences = buildPrefs(context.applicationContext)

    // ---------------- Reads ----------------

    /** The provider's API key; null when not configured. */
    fun getApiKey(provider: SttProvider): String? =
        prefs.getString("stt_${provider.name}_key", "")?.ifBlank { null }?.trim()

    /** Azure-specific region ("westeurope", …); null when not configured. */
    fun getAzureRegion(): String? =
        prefs.getString("stt_azure_region", "")?.ifBlank { null }?.trim()

    /** IBM-specific service instance URL; null when not configured. */
    fun getIbmInstanceUrl(): String? =
        prefs.getString("stt_ibm_watson_url", "")?.ifBlank { null }?.trim()

    /** Masked bullets for the Settings key-row summary. */
    fun maskedKey(provider: SttProvider): String? =
        getApiKey(provider)?.let { "•".repeat(minOf(it.length, 8)) }

    // ---------------- Writes ----------------

    fun setApiKey(provider: SttProvider, apiKey: String) {
        prefs.edit().putString("stt_${provider.name}_key", apiKey.trim()).apply()
    }

    fun setAzureRegion(region: String) {
        prefs.edit().putString("stt_azure_region", region.trim()).apply()
    }

    fun setIbmInstanceUrl(url: String) {
        prefs.edit().putString("stt_ibm_watson_url", url.trim()).apply()
    }

    // ---------------- Helpers ----------------

    /**
     * True when the given provider is fully configured (a non-blank key for
     * the key-only clouds, plus the region/URL for Azure and IBM).
     */
    fun isConfigured(provider: SttProvider): Boolean {
        if (getApiKey(provider).isNullOrBlank()) return false
        return when (provider) {
            SttProvider.CIHAZ -> true
            SttProvider.GROQ, SttProvider.GOOGLE_CLOUD -> true
            SttProvider.AZURE -> !getAzureRegion().isNullOrBlank()
            SttProvider.IBM_WATSON -> !getIbmInstanceUrl().isNullOrBlank()
        }
    }

    companion object {
        private const val PREFS_FILE_NAME = "recognition_stt_credentials"

        private fun buildPrefs(context: Context) = EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}