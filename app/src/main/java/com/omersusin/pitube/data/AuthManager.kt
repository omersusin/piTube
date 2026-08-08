package com.omersusin.pitube.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object AuthManager {
    private const val PREFS_NAME = "piTubeAuth"
    private const val KEY_RAW = "raw_cookies"

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(context, PREFS_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        cachedPrefs = prefs
        return prefs
    }

    fun saveCookies(context: Context, cookies: Map<String, String>) {
        val editor = getPrefs(context).edit()
        editor.clear()
        editor.putString(KEY_RAW, cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        cookies.forEach { (k, v) -> editor.putString(k, v) }
        editor.apply()
    }

    fun saveRawCookies(context: Context, raw: String) { getPrefs(context).edit().putString(KEY_RAW, raw).apply() }

    fun getRawCookies(context: Context): String {
        val prefs = getPrefs(context)
        val raw = prefs.getString(KEY_RAW, null)
        if (!raw.isNullOrBlank()) return raw
        return prefs.all.filterKeys { it != KEY_RAW }.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun getCookies(context: Context): Map<String, String> {
        val prefs = getPrefs(context)
        val map = prefs.all.filterKeys { it != KEY_RAW }.mapValues { it.value.toString() }.toMutableMap()
        if (map.isEmpty()) {
            val raw = prefs.getString(KEY_RAW, null) ?: return emptyMap()
            raw.split(";").forEach { part ->
                val idx = part.indexOf('=')
                if (idx > 0) map[part.substring(0, idx).trim()] = part.substring(idx + 1).trim()
            }
        }
        return map
    }

    fun isLoggedIn(context: Context): Boolean = getRawCookies(context).isNotBlank()

    fun logout(context: Context) {
        getPrefs(context).edit().clear().apply()
        cachedPrefs = null
    }
}
