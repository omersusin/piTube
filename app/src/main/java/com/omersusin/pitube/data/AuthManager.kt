package com.omersusin.pitube.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object AuthManager {
    private const val PREFS_NAME = "piTubeAuth"
    
    private fun getPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
            
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveCookies(context: Context, cookies: Map<String, String>) {
        val prefs = getPrefs(context)
        val editor = prefs.edit()
        cookies.forEach { (key, value) ->
            editor.putString(key, value)
        }
        editor.apply()
    }

    fun getCookies(context: Context): Map<String, String> {
        val prefs = getPrefs(context)
        return prefs.all.mapValues { it.value.toString() }
    }

    fun logout(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
    
    fun isLoggedIn(context: Context): Boolean {
        return getCookies(context).isNotEmpty()
    }
}
