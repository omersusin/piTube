package com.omersusin.pitube.data.local

/**
 * Validation and tidying for a Google session cookie header.
 *
 * Ported from Koda's YouTubeAuthUtils. The stored session is one opaque blob
 * (`k=v; k=v`) whether it came from the login WebView or from a hand-pasted
 * browser header, so the paste path must apply exactly the same bar the
 * WebView login applies before accepting a cookie string.
 */
object YouTubeAuthUtils {

    /**
     * Cookies carrying the secret the SAPISIDHASH is built from. YouTube hands
     * out the same value under the legacy name and its partitioned twin, so a
     * jar that is missing one usually still has the other.
     */
    private val SAPISID_NAMES = listOf("SAPISID", "__Secure-3PAPISID")

    fun getCookieValue(cookieString: String, cookieName: String): String? {
        // limit = 2: cookie values are base64-ish and can contain '=' padding,
        // so only the first separator delimits name from value.
        return cookieString.split(";")
            .map { it.trim().split("=", limit = 2) }
            .find { it.first() == cookieName }
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * The SAPISID value to sign with, under whichever name it arrived.
     */
    fun getSapisid(cookieString: String): String? =
        SAPISID_NAMES.firstNotNullOfOrNull { getCookieValue(cookieString, it) }

    /**
     * Tidy a hand-pasted cookie header into the single-line `k=v; k=v` form the
     * WebView jar produces. Handles the shapes people actually paste: a DevTools
     * copy of the request header with the `Cookie:` name still attached, a
     * wrapped multi-line copy, and stray surrounding quotes.
     */
    fun normalizeCookieString(raw: String): String {
        return raw.trim()
            .removeSurrounding("\"")
            .trim()
            .removePrefix("Cookie:")
            .removePrefix("cookie:")
            .replace(Regex("\\s*[\\r\\n]+\\s*"), " ")
            .trim()
            .trim(';')
            .trim()
    }

    /**
     * Names missing from [cookieString] that the app cannot work without: a
     * SAPISID to sign requests with, and the SID session itself. Deliberately
     * the same bar the WebView login applies — a stricter check here would
     * reject sessions that actually work.
     */
    fun missingRequiredCookies(cookieString: String): List<String> {
        val missing = mutableListOf<String>()
        if (getSapisid(cookieString) == null) missing += "SAPISID"
        if (getCookieValue(cookieString, "SID") == null) missing += "SID"
        return missing
    }

    fun getAuthorizationHeader(cookieString: String, origin: String = "https://music.youtube.com"): String? {
        val sapisid = getSapisid(cookieString) ?: return null
        val timestamp = System.currentTimeMillis() / 1000
        val input = "$timestamp $sapisid $origin"
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_$hash"
    }
}
