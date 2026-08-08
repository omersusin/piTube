package com.omersusin.pitube.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { LIGHT, DARK, SYSTEM }

class ThemePreferences(context: Context) {
    private val appContext: Context = context.applicationContext
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _amoledTheme = MutableStateFlow(prefs.getBoolean(KEY_AMOLED, false))
    val amoledTheme: StateFlow<Boolean> = _amoledTheme.asStateFlow()

    private val _saveVideoHistory = MutableStateFlow(prefs.getBoolean(KEY_SAVE_HISTORY, true))
    val saveVideoHistory: StateFlow<Boolean> = _saveVideoHistory.asStateFlow()

    private val _shortsEnabled = MutableStateFlow(prefs.getBoolean(KEY_SHORTS_ENABLED, true))
    val shortsEnabled: StateFlow<Boolean> = _shortsEnabled.asStateFlow()

    private val _videoQualityWifi = MutableStateFlow(prefs.getString(KEY_VIDEO_QUALITY_WIFI, "1080p") ?: "1080p")
    val videoQualityWifi: StateFlow<String> = _videoQualityWifi.asStateFlow()

    private val _videoQualityMobile = MutableStateFlow(prefs.getString(KEY_VIDEO_QUALITY_MOBILE, "720p") ?: "720p")
    val videoQualityMobile: StateFlow<String> = _videoQualityMobile.asStateFlow()

    private val _cacheEnabled = MutableStateFlow(prefs.getBoolean(KEY_CACHE_ENABLED, true))
    val cacheEnabled: StateFlow<Boolean> = _cacheEnabled.asStateFlow()

    private val _maxCacheSizeMb = MutableStateFlow(prefs.getLong(KEY_MAX_CACHE_SIZE_MB, 512L))
    val maxCacheSizeMb: StateFlow<Long> = _maxCacheSizeMb.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setAmoledTheme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AMOLED, enabled).apply()
        _amoledTheme.value = enabled
    }

    fun setSaveVideoHistory(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAVE_HISTORY, enabled).apply()
        _saveVideoHistory.value = enabled
    }

    fun setShortsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHORTS_ENABLED, enabled).apply()
        _shortsEnabled.value = enabled
    }

    fun setVideoQualityWifi(quality: String) {
        prefs.edit().putString(KEY_VIDEO_QUALITY_WIFI, quality).apply()
        _videoQualityWifi.value = quality
    }

    fun setVideoQualityMobile(quality: String) {
        prefs.edit().putString(KEY_VIDEO_QUALITY_MOBILE, quality).apply()
        _videoQualityMobile.value = quality
    }

    fun setCacheEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CACHE_ENABLED, enabled).apply()
        _cacheEnabled.value = enabled
    }

    fun setMaxCacheSizeMb(sizeMb: Long) {
        prefs.edit().putLong(KEY_MAX_CACHE_SIZE_MB, sizeMb).apply()
        _maxCacheSizeMb.value = sizeMb
    }

    private fun getThemeMode(): ThemeMode {
        val modeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(modeName ?: ThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    companion object {
        private const val PREFS_NAME = "pitube_theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_AMOLED = "amoled_theme"
        private const val KEY_SAVE_HISTORY = "save_video_history"
        private const val KEY_SHORTS_ENABLED = "shorts_enabled"
        private const val KEY_VIDEO_QUALITY_WIFI = "video_quality_wifi"
        private const val KEY_VIDEO_QUALITY_MOBILE = "video_quality_mobile"
        private const val KEY_CACHE_ENABLED = "cache_enabled"
        private const val KEY_MAX_CACHE_SIZE_MB = "max_cache_size_mb"
    }
}
