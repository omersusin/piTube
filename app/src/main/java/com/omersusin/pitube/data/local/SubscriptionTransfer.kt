package com.omersusin.pitube.data.local

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Subscription import/export across the de-facto YouTube-client formats
 * (Koda port): NewPipe/PipePipe `subscriptions.json`, Google Takeout
 * `subscriptions.csv` and OPML feeds. Imported channels land in the same
 * [SubscriptionRepository] the rest of the app reads; exports write the
 * NewPipe JSON and OPML shapes.
 */
object SubscriptionTransfer {

    private const val TAG = "SubscriptionTransfer"

    data class ImportedChannel(
        val channelId: String,
        val name: String = "",
        val avatarUrl: String = "",
    )

    data class ParseResult(
        val channels: List<ImportedChannel>,
        val format: String,
    )

    /** `UC...` id from a YouTube channel URL (`/channel/UC...`, `?channel_id=UC...`, handles give up). */
    fun extractChannelIdFromUrl(url: String): String? {
        val trimmed = url.trim()
        val direct = Regex("""/channel/([A-Za-z0-9_-]{10,})""").find(trimmed)?.groupValues?.get(1)
        if (direct != null) return direct
        val param = Regex("""channel_id=([A-Za-z0-9_-]{10,})""").find(trimmed)?.groupValues?.get(1)
        if (param != null) return param
        val bare = trimmed.substringAfterLast('/').takeIf { it.startsWith("UC") && it.length > 10 }
        return bare
    }

    private fun validChannels(channels: List<ImportedChannel>): List<ImportedChannel> =
        channels.filter { it.channelId.startsWith("UC") && it.channelId.length > 10 }
            .distinctBy { it.channelId }

    private fun subscriptionArray(text: String): JSONArray? {
        val t = text.trim()
        if (t.startsWith("[")) return try { JSONArray(t) } catch (_: Exception) { null }
        return try { JSONObject(t).optJSONArray("subscriptions") } catch (_: Exception) { null }
    }
    fun parseNewPipeJson(raw: String): List<ImportedChannel> {
        val array = subscriptionArray(raw) ?: return emptyList()
        val channels = mutableListOf<ImportedChannel>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.has("service_id") && obj.optInt("service_id", 0) != 0) continue
            val url = obj.optString("url").takeIf { it.isNotBlank() } ?: continue
            val id = extractChannelIdFromUrl(url) ?: continue
            channels += ImportedChannel(channelId = id, name = obj.optString("name"), avatarUrl = obj.optString("avatar_url"))
        }
        return validChannels(channels)
    }

    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>(); val cur = StringBuilder(); var inQ = false; var i = 0
        while (i < line.length) { val c = line[i]; if (c == '"') { if (inQ && i + 1 < line.length && line[i+1] == '"') { cur.append('"'); i += 2; continue } else { inQ = !inQ; i++; continue } }; if (c == ',' && !inQ) { out.add(cur.toString().trim()); cur.clear(); i++; continue }; cur.append(c); i++ }
        out.add(cur.toString().trim()); return out
    }
    fun parseTakeoutCsv(raw: String): List<ImportedChannel> {
        val lines = raw.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val header = splitCsvLine(lines.first()).map { it.trim().trim('"').lowercase() }
        val idIndex = header.indexOfFirst { it.contains("channel id") }
        val urlIndex = header.indexOfFirst { it.contains("channel url") }
        val titleIndex = header.indexOfFirst { it.contains("channel title") }
        if (idIndex < 0 && urlIndex < 0) return emptyList()
        val channels = mutableListOf<ImportedChannel>()
        for (line in lines.drop(1)) {
            val cells = splitCsvLine(line)
            fun cellAt(index: Int): String = cells.getOrNull(index)?.trim()?.trim('"').orEmpty()
            val id = cellAt(idIndex).takeIf { it.startsWith("UC") } ?: extractChannelIdFromUrl(cellAt(urlIndex)) ?: continue
            channels += ImportedChannel(channelId = id, name = cellAt(titleIndex))
        }
        return validChannels(channels)
    }

    fun parseOpml(raw: String): List<ImportedChannel> {
        val channels = mutableListOf<ImportedChannel>()
        Regex("""<outline[^>]*>""").findAll(raw).forEach { match ->
            val tag = match.value
            val xmlUrl = Regex("""xmlUrl="([^"]*)"""").find(tag)?.groupValues?.get(1) ?: return@forEach
            val id = extractChannelIdFromUrl(xmlUrl) ?: return@forEach
            val title = Regex("""text="([^"]*)"""").find(tag)?.groupValues?.get(1).orEmpty()
            channels += ImportedChannel(channelId = id, name = title)
        }
        return validChannels(channels)
    }

    fun countForeignServiceEntries(text: String): Int {
        val arr = subscriptionArray(text) ?: return 0
        return (0 until arr.length()).count { i -> val o = arr.optJSONObject(i) ?: return@count false; o.has("service_id") && o.optInt("service_id", 0) != 0 }
    }

    /** Auto-detect the format and parse. Returns null when nothing matched. */
    fun parse(raw: String): ParseResult? {
        val trimmed = raw.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        return when {
            trimmed.startsWith("{") || trimmed.startsWith("[") -> {
                val channels = parseNewPipeJson(trimmed)
                if (channels.isNotEmpty()) ParseResult(channels, "NewPipe JSON") else null
            }
            trimmed.startsWith("<") -> {
                val channels = parseOpml(trimmed)
                if (channels.isNotEmpty()) ParseResult(channels, "OPML") else null
            }
            else -> {
                val channels = parseTakeoutCsv(trimmed)
                if (channels.isNotEmpty()) ParseResult(channels, "Google Takeout CSV") else null
            }
        }
    }

    suspend fun apply(context: Context, channels: List<ImportedChannel>): Int {
        val repository = SubscriptionRepository.getInstance(context)
        var added = 0
        channels.forEach { channel ->
            runCatching {
                repository.subscribe(
                    ChannelSubscription(
                        channelId = channel.channelId,
                        channelName = channel.name.ifBlank { channel.channelId },
                        channelThumbnail = channel.avatarUrl,
                    )
                )
                added++
            }.onFailure { Log.w(TAG, "Failed subscribing ${channel.channelId}", it) }
        }
        return added
    }

    fun buildNewPipeJson(channels: List<ChannelSubscription>): String {
        val array = JSONArray()
        channels.forEach { channel ->
            val item = JSONObject()
                .put("service_id", 0)
                .put("url", "https://www.youtube.com/channel/${channel.channelId}")
                .put("name", channel.channelName)
                .put("avatar_url", channel.channelThumbnail)
            array.put(item)
        }
        return JSONObject()
            .put("app_version", "piTube")
            .put("app_version_int", 1)
            .put("subscriptions", array)
            .toString(2)
    }

    fun buildOpml(channels: List<ChannelSubscription>): String {
        val outlines = channels.joinToString("\n") { channel ->
            val name = channel.channelName
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            "    <outline text=\"$name\" title=\"$name\" type=\"rss\" " +
                "xmlUrl=\"https://www.youtube.com/feeds/videos.xml?channel_id=${channel.channelId}\"/>"
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<opml version=\"1.1\">\n" +
            "  <body>\n" +
            "    <outline text=\"piTube Subscriptions\" title=\"piTube Subscriptions\">\n" +
            outlines + "\n" +
            "    </outline>\n" +
            "  </body>\n" +
            "</opml>\n"
    }
}
