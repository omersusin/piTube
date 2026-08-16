package com.omersusin.pitube.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.omersusin.pitube.recognition.VoiceRecognitionSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.recognitionPreferencesDataStore: DataStore<androidx.datastore.preferences.core.Preferences> by preferencesDataStore(name = "recognition_preferences")

enum class RecognitionProvider(
    val storedValue: String,
) {
    SHAZAM("shazam"),
    AUDD("audd"),
    ACRCLOUD("acrcloud");

    companion object {
        fun fromStored(value: String?): RecognitionProvider =
            entries.firstOrNull { it.storedValue == value } ?: SHAZAM
    }
}

/**
 * How voice recognition is performed. Cihaz STT uses Android's built-in
 * `SpeechRecognizer` (zero-config default); the cloud providers each need the
 * person's own API key entered in Settings and fall back to Cihaz STT on
 * failure.
 */
enum class SttProvider(
    val storedValue: String,
) {
    CIHAZ("cihaz"),
    GROQ("groq"),
    IBM_WATSON("ibm_watson"),
    AZURE("azure"),
    GOOGLE_CLOUD("google_cloud");

    val isCloud: Boolean
        get() = this != CIHAZ

    /** Maps to the [VoiceRecognitionSource] for the result card/log. */
    fun toSource(): VoiceRecognitionSource = when (this) {
        CIHAZ -> VoiceRecognitionSource.ON_DEVICE
        GROQ -> VoiceRecognitionSource.GROQ
        IBM_WATSON -> VoiceRecognitionSource.IBM_WATSON
        AZURE -> VoiceRecognitionSource.AZURE
        GOOGLE_CLOUD -> VoiceRecognitionSource.GOOGLE_CLOUD
    }

    companion object {
        fun fromStored(value: String?): SttProvider =
            entries.firstOrNull { it.storedValue == value } ?: CIHAZ
    }
}

/**
 * What to do with a recorded sample when recognition cannot complete.
 * Mirrors Audile's fallback policy model: SAVE_AND_RETRY is only offered for
 * connectivity failures (the recording is kept locally and recognized again
 * automatically once a network is available again).
 */
enum class FallbackPolicy(
    val storedValue: String,
    val savesRecording: Boolean,
    val retries: Boolean = false,
) {
    IGNORE("ignore", savesRecording = false),
    SAVE("save", savesRecording = true),
    SAVE_AND_RETRY("save_retry", savesRecording = true, retries = true);

    companion object {
        fun fromStored(value: String?): FallbackPolicy =
            entries.firstOrNull { it.storedValue == value } ?: IGNORE
    }
}

class RecognitionPreferences(context: Context) {
    private val context: Context = context.applicationContext

    private object Keys {
        val PROVIDER = stringPreferencesKey("recognition_provider")
        val STT_PROVIDER = stringPreferencesKey("recognition_stt_provider")
        val FALLBACK_BAD_INTERNET = stringPreferencesKey("fallback_bad_internet")
        val FALLBACK_NO_MATCH = stringPreferencesKey("fallback_no_match")
        val FALLBACK_OTHER = stringPreferencesKey("fallback_other")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("recognition_notifications_enabled")
        val FLOATING_BUTTON_ENABLED = booleanPreferencesKey("recognition_floating_button_enabled")
    }

    val provider: Flow<RecognitionProvider> = context.recognitionPreferencesDataStore.data
        .map { RecognitionProvider.fromStored(it[Keys.PROVIDER]) }

    val sttProvider: Flow<SttProvider> = context.recognitionPreferencesDataStore.data
        .map { SttProvider.fromStored(it[Keys.STT_PROVIDER]) }

    val fallbackBadInternet: Flow<FallbackPolicy> = context.recognitionPreferencesDataStore.data
        .map { FallbackPolicy.fromStored(it[Keys.FALLBACK_BAD_INTERNET]) }

    val fallbackNoMatch: Flow<FallbackPolicy> = context.recognitionPreferencesDataStore.data
        .map { FallbackPolicy.fromStored(it[Keys.FALLBACK_NO_MATCH]) }

    val fallbackOther: Flow<FallbackPolicy> = context.recognitionPreferencesDataStore.data
        .map { FallbackPolicy.fromStored(it[Keys.FALLBACK_OTHER]) }

    val notificationsEnabled: Flow<Boolean> = context.recognitionPreferencesDataStore.data
        .map { it[Keys.NOTIFICATIONS_ENABLED] ?: false }

    val floatingButtonEnabled: Flow<Boolean> = context.recognitionPreferencesDataStore.data
        .map { it[Keys.FLOATING_BUTTON_ENABLED] ?: false }

    suspend fun setSttProvider(value: SttProvider) {
        context.recognitionPreferencesDataStore.edit { it[Keys.STT_PROVIDER] = value.storedValue }
    }

    suspend fun setProvider(value: RecognitionProvider) {
        context.recognitionPreferencesDataStore.edit { it[Keys.PROVIDER] = value.storedValue }
    }

    suspend fun setFallbackBadInternet(value: FallbackPolicy) {
        context.recognitionPreferencesDataStore.edit { it[Keys.FALLBACK_BAD_INTERNET] = value.storedValue }
    }

    suspend fun setFallbackNoMatch(value: FallbackPolicy) {
        context.recognitionPreferencesDataStore.edit { it[Keys.FALLBACK_NO_MATCH] = value.storedValue }
    }

    suspend fun setFallbackOther(value: FallbackPolicy) {
        context.recognitionPreferencesDataStore.edit { it[Keys.FALLBACK_OTHER] = value.storedValue }
    }

    suspend fun setNotificationsEnabled(value: Boolean) {
        context.recognitionPreferencesDataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = value }
    }

    suspend fun setFloatingButtonEnabled(value: Boolean) {
        context.recognitionPreferencesDataStore.edit { it[Keys.FLOATING_BUTTON_ENABLED] = value }
    }

    suspend fun fallbackState(): RecognitionFallbackState =
        RecognitionFallbackState(
            badInternet = fallbackBadInternet.first(),
            noMatch = fallbackNoMatch.first(),
            other = fallbackOther.first(),
        )
}

data class RecognitionFallbackState(
    val badInternet: FallbackPolicy,
    val noMatch: FallbackPolicy,
    val other: FallbackPolicy,
) {
    fun forType(type: RecognitionFailureType): FallbackPolicy =
        when (type) {
            RecognitionFailureType.BAD_CONNECTION -> badInternet
            RecognitionFailureType.NO_MATCH -> noMatch
            RecognitionFailureType.OTHER -> other
        }
}

enum class RecognitionFailureType {
    BAD_CONNECTION,
    NO_MATCH,
    OTHER,
}