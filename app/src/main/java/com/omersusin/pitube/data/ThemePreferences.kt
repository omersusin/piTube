package com.omersusin.pitube.data

import android.content.Context
import android.content.SharedPreferences
import com.omersusin.pitube.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemePreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(getThemeModePreference())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _sponsorBlockEnabled = MutableStateFlow(prefs.getBoolean(KEY_SPONSORBLOCK, true))
    val sponsorBlockEnabled: StateFlow<Boolean> = _sponsorBlockEnabled.asStateFlow()

    private val _zenMode = MutableStateFlow(prefs.getBoolean(KEY_ZEN_MODE, false))
    val zenMode: StateFlow<Boolean> = _zenMode.asStateFlow()

    private val _hideShorts = MutableStateFlow(prefs.getBoolean(KEY_HIDE_SHORTS, false))
    val hideShorts: StateFlow<Boolean> = _hideShorts.asStateFlow()

    private val _hideCounters = MutableStateFlow(prefs.getBoolean(KEY_HIDE_COUNTERS, false))
    val hideCounters: StateFlow<Boolean> = _hideCounters.asStateFlow()

    private val _hideComments = MutableStateFlow(prefs.getBoolean(KEY_HIDE_COMMENTS, false))
    val hideComments: StateFlow<Boolean> = _hideComments.asStateFlow()

    private val _autoExpandDesc = MutableStateFlow(prefs.getBoolean(KEY_AUTO_EXPAND_DESC, false))
    val autoExpandDesc: StateFlow<Boolean> = _autoExpandDesc.asStateFlow()

    private val _hideLikeButtons = MutableStateFlow(prefs.getBoolean(KEY_HIDE_LIKE_BUTTONS, false))
    val hideLikeButtons: StateFlow<Boolean> = _hideLikeButtons.asStateFlow()

    private val _volumeNormalization = MutableStateFlow(prefs.getBoolean(KEY_VOLUME_NORM, false))
    val volumeNormalization: StateFlow<Boolean> = _volumeNormalization.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f))
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _videoQualityWifi = MutableStateFlow(prefs.getString(KEY_VIDEO_QUALITY_WIFI, "1080p") ?: "1080p")
    val videoQualityWifi: StateFlow<String> = _videoQualityWifi.asStateFlow()

    private val _videoQualityMobile = MutableStateFlow(prefs.getString(KEY_VIDEO_QUALITY_MOBILE, "720p") ?: "720p")
    val videoQualityMobile: StateFlow<String> = _videoQualityMobile.asStateFlow()

    init {
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KEY_THEME_MODE -> _themeMode.value = getThemeModePreference()
                KEY_SPONSORBLOCK -> _sponsorBlockEnabled.value = prefs.getBoolean(KEY_SPONSORBLOCK, true)
                KEY_ZEN_MODE -> _zenMode.value = prefs.getBoolean(KEY_ZEN_MODE, false)
                KEY_HIDE_SHORTS -> _hideShorts.value = prefs.getBoolean(KEY_HIDE_SHORTS, false)
                KEY_HIDE_COUNTERS -> _hideCounters.value = prefs.getBoolean(KEY_HIDE_COUNTERS, false)
                KEY_HIDE_COMMENTS -> _hideComments.value = prefs.getBoolean(KEY_HIDE_COMMENTS, false)
                KEY_AUTO_EXPAND_DESC -> _autoExpandDesc.value = prefs.getBoolean(KEY_AUTO_EXPAND_DESC, false)
                KEY_HIDE_LIKE_BUTTONS -> _hideLikeButtons.value = prefs.getBoolean(KEY_HIDE_LIKE_BUTTONS, false)
                KEY_VOLUME_NORM -> _volumeNormalization.value = prefs.getBoolean(KEY_VOLUME_NORM, false)
                KEY_PLAYBACK_SPEED -> _playbackSpeed.value = prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f)
                KEY_VIDEO_QUALITY_WIFI -> _videoQualityWifi.value = prefs.getString(KEY_VIDEO_QUALITY_WIFI, "1080p") ?: "1080p"
                KEY_VIDEO_QUALITY_MOBILE -> _videoQualityMobile.value = prefs.getString(KEY_VIDEO_QUALITY_MOBILE, "720p") ?: "720p"
            }
        }
    }

    private fun getThemeModePreference(): ThemeMode {
        val modeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(modeName ?: ThemeMode.SYSTEM.name)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setSponsorBlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPONSORBLOCK, enabled).apply()
        _sponsorBlockEnabled.value = enabled
    }

    fun setZenMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ZEN_MODE, enabled).apply()
        _zenMode.value = enabled
    }

    fun setHideShorts(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_SHORTS, enabled).apply()
        _hideShorts.value = enabled
    }

    fun setHideCounters(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_COUNTERS, enabled).apply()
        _hideCounters.value = enabled
    }

    fun setHideComments(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_COMMENTS, enabled).apply()
        _hideComments.value = enabled
    }

    fun setAutoExpandDesc(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_EXPAND_DESC, enabled).apply()
        _autoExpandDesc.value = enabled
    }

    fun setHideLikeButtons(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_LIKE_BUTTONS, enabled).apply()
        _hideLikeButtons.value = enabled
    }

    fun setVolumeNormalization(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOLUME_NORM, enabled).apply()
        _volumeNormalization.value = enabled
    }

    fun setPlaybackSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_PLAYBACK_SPEED, speed).apply()
        _playbackSpeed.value = speed
    }

    fun setVideoQualityWifi(quality: String) {
        prefs.edit().putString(KEY_VIDEO_QUALITY_WIFI, quality).apply()
        _videoQualityWifi.value = quality
    }

    fun setVideoQualityMobile(quality: String) {
        prefs.edit().putString(KEY_VIDEO_QUALITY_MOBILE, quality).apply()
        _videoQualityMobile.value = quality
    }

    fun getDefaultVideoQuality(): String {
        val cm = prefs.context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val isMetered = cm?.isActiveNetworkMetered ?: false
        return if (isMetered) _videoQualityMobile.value else _videoQualityWifi.value
    }

    companion object {
        private const val PREFS_NAME = "pitube_theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_SPONSORBLOCK = "sponsorblock"
        private const val KEY_ZEN_MODE = "zen_mode"
        private const val KEY_HIDE_SHORTS = "hide_shorts"
        private const val KEY_HIDE_COUNTERS = "hide_counters"
        private const val KEY_HIDE_COMMENTS = "hide_comments"
        private const val KEY_AUTO_EXPAND_DESC = "auto_expand_desc"
        private const val KEY_HIDE_LIKE_BUTTONS = "hide_like_buttons"
        private const val KEY_VOLUME_NORM = "vol_norm"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"
        private const val KEY_VIDEO_QUALITY_WIFI = "video_quality_wifi"
        private const val KEY_VIDEO_QUALITY_MOBILE = "video_quality_mobile"
    }
}
