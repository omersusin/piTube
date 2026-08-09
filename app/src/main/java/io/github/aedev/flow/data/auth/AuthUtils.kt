package io.github.aedev.flow.data.auth

import android.content.Context
import java.security.MessageDigest

object AuthUtils {
    private val SAPISID_NAMES = listOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID", "APISID")
    private val REFRESHABLE = setOf(
        "SID", "HSID", "SSID", "APISID", "SAPISID", "LOGIN_INFO", "SIDCC",
        "__Secure-1PSID", "__Secure-3PSID", "__Secure-1PAPISID", "__Secure-3PAPISID",
        "__Secure-1PSIDTS", "__Secure-3PSIDTS", "__Secure-1PSIDCC", "__Secure-3PSIDCC"
    )

    fun getCookieValue(cookieString: String, name: String): String? =
        cookieString.split(";").map { it.trim().split("=", limit = 2) }
            .find { it.first() == name }?.getOrNull(1)?.takeIf { it.isNotBlank() }

    fun getSapisid(cookieString: String): String? = SAPISID_NAMES.firstNotNullOfOrNull { getCookieValue(cookieString, it) }

    fun normalize(raw: String): String = raw.trim().removeSurrounding("\"").trim()
        .removePrefix("Cookie:").removePrefix("cookie:").replace(Regex("\\s*[\\r\\n]+\\s*"), " ")
        .trim().trim(';').trim()

    fun missingRequired(cookieString: String): List<String> {
        val m = mutableListOf<String>()
        if (getSapisid(cookieString) == null) m += "SAPISID"
        if (getCookieValue(cookieString, "SID") == null) m += "SID"
        return m
    }

    fun mergeCookies(existing: String, updates: Map<String, String>): String {
        val merged = LinkedHashMap<String, String>()
        existing.split(";").forEach { part ->
            val pair = part.trim().split("=", limit = 2)
            val name = pair.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@forEach
            merged[name] = pair.getOrNull(1).orEmpty()
        }
        updates.forEach { (name, value) ->
            if (value.isBlank()) return@forEach
            if (name in REFRESHABLE || merged.containsKey(name)) merged[name] = value
        }
        return merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun refreshFromResponse(context: Context, resp: okhttp3.Response) {
        val updates = mutableMapOf<String, String>()
        for (h in resp.headers("Set-Cookie")) {
            val pair = h.substringBefore(";").trim().split("=", limit = 2)
            if (pair.size == 2 && pair[1].isNotBlank() && pair[0].trim() in REFRESHABLE) updates[pair[0].trim()] = pair[1]
        }
        if (updates.isEmpty()) return
        val existing = AuthManager.getRawCookies(context)
        if (existing.isBlank()) return
        val merged = mergeCookies(existing, updates)
        if (merged != existing) {
            AuthManager.saveRawCookies(context, merged)
            io.github.aedev.flow.innertube.YouTube.cookie = merged
        }
    }
}
