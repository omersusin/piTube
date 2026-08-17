package com.omersusin.pitube.data.local

/**
 * A full InnerTube credential bundle, the shape the reference clients
 * (OuterTune / ViVi Music / ArchiveTune) exchange as one pasted token.
 */
data class YouTubeToken(
    val cookie: String,
    val visitorData: String? = null,
    val dataSyncId: String? = null,
    val poToken: String? = null,
    val accountName: String? = null,
    val accountEmail: String? = null,
    val accountChannelHandle: String? = null,
) {
    /** True when the token carries a usable YouTube login cookie. */
    val isValid: Boolean get() = YouTubeAuthUtils.getSapisid(cookie) != null
}

/**
 * Parses the OuterTune/ViVi-style advanced login token, e.g.:
 *
 * ```
 * ***INNERTUBE COOKIE*** =SAPISID=...; SID=...
 * ***VISITOR DATA*** =Cg...
 * ***DATASYNC ID*** =118371353090829304832
 * ***PO TOKEN*** =...
 * ***ACCOUNT NAME*** =Example User
 * ***ACCOUNT EMAIL*** =user@example.com
 * ***ACCOUNT CHANNEL HANDLE*** =@example
 * ```
 *
 * Each `***MARKER*** =value` line is optional; the same parser also accepts a
 * bare cookie header (`k=v; k=v`) pasted on its own, which the token simply
 * wraps. Value extraction is deliberately lenient (`substringAfter("=")` so a
 * value containing `=` survives) and multiline runs are joined, matching how
 * people copy tokens out of a browser or another app.
 */
object YouTubeTokenParser {

    /** White-space tolerant regex for `***MARKER*** =…` lines. */
    private val fieldRegex = Regex("""^\*\*\*([^*]+?)\*\*\*\s*=\s*(.*)$""")

    /**
     * Trim one line into a single-space run: the values come from a clipboard
     * copy that can be wrapped at arbitrary widths, so newlines inside a value
     * (notably the cookie header list) are joined with a single space.
     */
    private fun cleanValue(raw: String): String =
        raw.trim()
            .removeSurrounding("\"")
            .replace(Regex("\\s*[\\r\\n]+\\s*"), " ")
            .trim()

    fun parse(raw: String): YouTubeToken {
        var cookie: String? = null
        var visitorData: String? = null
        var dataSyncId: String? = null
        var poToken: String? = null
        var accountName: String? = null
        var accountEmail: String? = null
        var accountChannelHandle: String? = null

        raw.split("\n").forEach { line ->
            val match = fieldRegex.matchEntire(line.trim()) ?: return@forEach
            val marker = match.groupValues[1].trim()
            val value = cleanValue(match.groupValues[2])
            if (value.isEmpty()) return@forEach
            when (marker.uppercase()) {
                "INNERTUBE COOKIE", "YOUTUBE COOKIE", "COOKIE" -> cookie = value
                "VISITOR DATA", "VISITORDATA" -> visitorData = value
                "DATASYNC ID", "DATASYNCID", "DATASYNC" -> dataSyncId = value
                "PO TOKEN", "POTOKEN" -> poToken = value
                "ACCOUNT NAME", "ACCOUNTNAME", "NAME" -> accountName = value
                "ACCOUNT EMAIL", "ACCOUNTEMAIL", "EMAIL" -> accountEmail = value
                "ACCOUNT CHANNEL HANDLE", "ACCOUNTCHANNELHANDLE", "CHANNEL HANDLE", "HANDLE" ->
                    accountChannelHandle = value
            }
        }

        // Bare cookie header pasted with no markers → wrap it as the cookie.
        val resolvedCookie = cookie
            ?: cleanValue(raw).takeIf { it.isNotBlank() && !fieldRegex.containsMatchIn(raw) }

        return YouTubeToken(
            cookie = resolvedCookie.orEmpty(),
            visitorData = visitorData,
            dataSyncId = dataSyncId,
            poToken = poToken,
            accountName = accountName,
            accountEmail = accountEmail,
            accountChannelHandle = accountChannelHandle,
        )
    }
}