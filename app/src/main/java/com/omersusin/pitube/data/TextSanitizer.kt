package com.omersusin.pitube.data

import android.text.Html

fun sanitizeYouTubeText(raw: String): String {
    if (raw.isEmpty()) return ""
    val stripped = raw.replace(Regex("<[^>]*>"), " ")
    return Html.fromHtml(stripped, Html.FROM_HTML_MODE_LEGACY).toString().trim()
}
