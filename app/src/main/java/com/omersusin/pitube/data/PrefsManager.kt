package com.omersusin.pitube.data

import android.content.Context

object PrefsManager {
    private const val PREFS = "pitube_prefs"
    private const val KEY_SPONSORBLOCK = "sponsorblock"
    private const val KEY_ZEN = "zen_mode"
    private const val KEY_VOL_NORM = "vol_norm"
    private const val KEY_SPEED = "playback_speed"
    private const val KEY_HIDE_SHORTS = "hide_shorts"
    private const val KEY_HIDE_COUNTERS = "hide_counters"
    private const val KEY_HIDE_COMMENTS = "hide_comments"
    private const val KEY_AUTO_EXPAND = "auto_expand_desc"
    private const val KEY_HIDE_LIKES = "hide_like_buttons"
    private const val KEY_DEBUG = "debug_auth"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSponsorBlockEnabled(context: Context) = prefs(context).getBoolean(KEY_SPONSORBLOCK, true)
    fun setSponsorBlockEnabled(context: Context, e: Boolean) = prefs(context).edit().putBoolean(KEY_SPONSORBLOCK, e).apply()
    fun isZenMode(context: Context) = prefs(context).getBoolean(KEY_ZEN, false)
    fun setZenMode(context: Context, e: Boolean) = prefs(context).edit().putBoolean(KEY_ZEN, e).apply()
    fun isVolumeNormalizationEnabled(context: Context) = prefs(context).getBoolean(KEY_VOL_NORM, false)
    fun setVolumeNormalizationEnabled(context: Context, e: Boolean) = prefs(context).edit().putBoolean(KEY_VOL_NORM, e).apply()
    fun getPlaybackSpeed(context: Context) = prefs(context).getFloat(KEY_SPEED, 1.0f)
    fun setPlaybackSpeed(context: Context, s: Float) = prefs(context).edit().putFloat(KEY_SPEED, s).apply()
    fun isHideShorts(context: Context) = prefs(context).getBoolean(KEY_HIDE_SHORTS, false)
    fun setHideShorts(context: Context, e: Boolean) = prefs(context).edit().putBoolean(KEY_HIDE_SHORTS, e).apply()
    fun isHideCounters(context: Context) = prefs(context).getBoolean(KEY_HIDE_COUNTERS, false)
    fun setHideCounters(context: Context, e: Boolean) = prefs(context).edit().putBoolean(KEY_HIDE_COUNTERS, e).apply()
    fun isHideComments(context: Context) = prefs(context).getBoolean(KEY_HIDE_COMMENTS, false)
    fun setHideComments(context: Context, e: Boolean) = prefs(context).edit().putBoolean(KEY_HIDE_COMMENTS, e).apply()
    fun isAutoExpandDesc(context: Context) = prefs(context).getBoolean(KEY_AUTO_EXPAND, false)
    fun setAutoExpandDesc(context: Context, e: Boolean) = prefs(context).edit().putBoolean(KEY_AUTO_EXPAND, e).apply()
    fun isHideLikeButtons(context: Context) = prefs(context).getBoolean(KEY_HIDE_LIKES, false)
    fun setHideLikeButtons(context: Context, e: Boolean) = prefs(context).edit().putBoolean(KEY_HIDE_LIKES, e).apply()
    fun isDebugAuth(context: Context) = prefs(context).getBoolean(KEY_DEBUG, false)
    fun setDebugAuth(context: Context, e: Boolean) = prefs(context).edit().putBoolean(KEY_DEBUG, e).apply()
}
