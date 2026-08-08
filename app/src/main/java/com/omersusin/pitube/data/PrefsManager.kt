package com.omersusin.pitube.data

import android.content.Context

object PrefsManager {
    private const val PREFS = "pitube_prefs"
    private const val KEY_SPONSORBLOCK = "sponsorblock"
    private const val KEY_ZEN = "zen_mode"
    private const val KEY_VOL_NORM = "vol_norm"

    fun isSponsorBlockEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SPONSORBLOCK, true)

    fun setSponsorBlockEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SPONSORBLOCK, enabled).apply()
    }

    fun isZenMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ZEN, false)

    fun setZenMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ZEN, enabled).apply()
    }

    fun isVolumeNormalizationEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_VOL_NORM, false)

    fun setVolumeNormalizationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_VOL_NORM, enabled).apply()
    }
}
