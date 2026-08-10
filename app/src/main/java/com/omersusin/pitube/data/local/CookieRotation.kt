package com.omersusin.pitube.data.local

/**
 * Keeps the stored Google session fresh by folding in the cookies YouTube
 * re-issues during a live session.
 *
 * Ported from Koda's SessionCookieJar/YouTubeAuthUtils. Google rotates the
 * session cookies (notably the `__Secure-1PSIDTS` / `__Secure-3PSIDTS` pair)
 * as requests go out; a session that keeps replaying the value captured at
 * login eventually gets answered as signed out (`logged_in: 0`, empty feed)
 * while the app still thinks it is signed in because a cookie string exists.
 */
object CookieRotation {

    /**
     * Cookies Google rotates during a live session, on top of whatever the jar
     * already carries.
     */
    private val REFRESHABLE_COOKIE_NAMES = setOf(
        "SID", "HSID", "SSID", "APISID", "SAPISID", "LOGIN_INFO", "SIDCC",
        "__Secure-1PSID", "__Secure-3PSID",
        "__Secure-1PAPISID", "__Secure-3PAPISID",
        "__Secure-1PSIDTS", "__Secure-3PSIDTS",
        "__Secure-1PSIDCC", "__Secure-3PSIDCC"
    )

    /**
     * Fold freshly issued `Set-Cookie` header values into a stored cookie
     * string. Only names already present, or in [REFRESHABLE_COOKIE_NAMES],
     * are taken — every response also sets throwaway cookies (YSC,
     * VISITOR_INFO1_LIVE, ad and consent state) that would bloat the header
     * without authenticating anything. Order is preserved and nothing is ever
     * removed: this can only make a session fresher, never tear one down.
     */
    fun mergeCookies(existing: String, setCookieHeaders: List<String>): String {
        val updates = LinkedHashMap<String, String>()
        setCookieHeaders.forEach { header ->
            val nameValue = header.substringBefore(";").trim()
            val pair = nameValue.split("=", limit = 2)
            val name = pair.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@forEach
            val value = pair.getOrNull(1).orEmpty()
            if (value.isBlank()) return@forEach
            updates[name] = value
        }
        if (updates.isEmpty()) return existing

        val merged = LinkedHashMap<String, String>()
        existing.split(";").forEach { part ->
            val pair = part.trim().split("=", limit = 2)
            val name = pair.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@forEach
            merged[name] = pair.getOrNull(1).orEmpty()
        }
        updates.forEach { (name, value) ->
            if (name in REFRESHABLE_COOKIE_NAMES || merged.containsKey(name)) {
                merged[name] = value
            }
        }
        return merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }
}
