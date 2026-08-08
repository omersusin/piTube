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

    fun isSponsorBlockEnabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SPONSORBLOCK, true)
    fun setSponsorBlockEnabled(context: Context, enabled: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SPONSORBLOCK, enabled).apply() }

    fun isZenMode(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ZEN, false)
    fun setZenMode(context: Context, enabled: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ZEN, enabled).apply() }

    fun isVolumeNormalizationEnabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_VOL_NORM, false)
    fun setVolumeNormalizationEnabled(context: Context, enabled: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_VOL_NORM, enabled).apply() }

    fun getPlaybackSpeed(context: Context): Float = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(KEY_SPEED, 1.0f)
    fun setPlaybackSpeed(context: Context, speed: Float) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(KEY_SPEED, speed).apply() }

    fun isHideShorts(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_HIDE_SHORTS, false)
    fun setHideShorts(context: Context, enabled: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_HIDE_SHORTS, enabled).apply() }

    fun isHideCounters(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_HIDE_COUNTERS, false)
    fun setHideCounters(context: Context, enabled: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_HIDE_COUNTERS, enabled).apply() }

    fun isHideComments(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_HIDE_COMMENTS, false)
    fun setHideComments(context: Context, enabled: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_HIDE_COMMENTS, enabled).apply() }

    fun isAutoExpandDesc(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_EXPAND, false)
    fun setAutoExpandDesc(context: Context, enabled: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_EXPAND, enabled).apply() }

    fun isHideLikeButtons(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_HIDE_LIKES, false)
    fun setHideLikeButtons(context: Context, enabled: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_HIDE_LIKES, enabled).apply() }
}
