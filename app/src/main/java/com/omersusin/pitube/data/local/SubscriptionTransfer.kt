package com.omersusin.pitube.data.local

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.firstOrNull
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

    fun parseNewPipeJson(raw: String): List<ImportedChannel> {
        val root = JSONObject(raw)
        val array = root.optJSONArray("subscriptions") ?: return emptyList()
        val channels = mutableListOf<ImportedChannel>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val url = item.optString("url")
            val id = extractChannelIdFromUrl(url) ?: continue
            channels += ImportedChannel(
                channelId = id,
                name = item.optString("name"),
                avatarUrl = item.optString("avatar_url"),
            )
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

    /** Auto-detect the format and parse. Returns null when nothing matched. */
    fun parse(raw: String): ParseResult? {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("{") || trimmed.startsWith("[") -> {
                val channels = parseSubscriptionsJson(trimmed)
                if (channels.isNotEmpty()) ParseResult(channels, "JSON") else null
            }
            trimmed.startsWith("<?xml") || trimmed.contains("<opml") -> {
                val channels = parseOpml(trimmed)
                if (channels.isNotEmpty()) ParseResult(channels, "OPML") else null
            }
            trimmed.lines().firstOrNull()?.contains(',') == true -> {
                val channels = parseTakeoutCsv(trimmed)
                if (channels.isNotEmpty()) ParseResult(channels, "Google Takeout CSV") else null
            }
            else -> null
        }
    }

    /**
     * JSON subscriptions in any de-facto client shape:
     *  - NewPipe / PipePipe / Piped: `{"app_version":..,"subscriptions":[{"url","name","avatar_url"}]}`
     *  - LibreTube backup:          `{"subscriptions":[{"channel_id","name","avatar":[...]}]}` (+ metadata keys)
     *  - FreeTube:                  bare `[{"id","name","thumbnail"}]`
     */
    fun parseSubscriptionsJson(raw: String): List<ImportedChannel> {
        val root = runCatching { JSONObject(raw) }.getOrNull()
        val array = when {
            root != null -> root.optJSONArray("subscriptions")
                ?: root.optJSONArray("channels")
                ?: JSONArray()
            else -> runCatching { JSONArray(raw) }.getOrNull() ?: JSONArray()
        }
        val channels = mutableListOf<ImportedChannel>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue

            // id: NewPipe/Piped carry it inside `url`; LibreTube uses `channel_id`;
            // FreeTube uses `id`.
            val id = extractChannelIdFromUrl(item.optString("url"))
                ?: item.optString("channel_id").takeIf { it.startsWith("UC") }
                ?: item.optString("id").takeIf { it.startsWith("UC") }
                ?: continue

            // avatar: string (NewPipe/Piped) or array of URL strings (LibreTube).
            val avatar = when {
                item.optString("avatar_url").isNotBlank() -> item.optString("avatar_url")
                item.optString("thumbnail").isNotBlank() -> item.optString("thumbnail")
                else -> {
                    val arr = item.optJSONArray("avatar")
                    if (arr != null && arr.length() > 0) {
                        // Prefer the largest variant (usually last), as LibreTube orders by size.
                        var best = ""
                        for (k in 0 until arr.length()) {
                            val v = when (val e = arr.opt(k)) {
                                is String -> e
                                is JSONObject -> e.optString("url")
                                else -> ""
                            }
                            if (v.isNotBlank()) best = v
                        }
                        best
                    } else ""
                }
            }

            channels += ImportedChannel(
                channelId = id,
                name = item.optString("name").ifBlank { item.optString("title") },
                avatarUrl = avatar,
            )
        }
        return validChannels(channels)
    }

    /**
     * Import the given channels:
     *  - local store updated in ONE batched transaction ([SubscriptionRepository.subscribeAll]),
     *    which is what makes 2000+ channel imports fast;
     *  - when signed in, every canonical channel is also pushed to the YouTube account
     *    so a later library sync does not silently prune freshly imported rows.
     *
     * Returns the number of channels present locally after the import.
     */
    suspend fun apply(
        context: Context,
        channels: List<ImportedChannel>,
        pushToAccount: Boolean = true,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Int {
        val repository = SubscriptionRepository.getInstance(context)
        val entities = channels.map { channel ->
            ChannelSubscription(
                channelId = channel.channelId,
                channelName = channel.name.ifBlank { channel.channelId },
                channelThumbnail = channel.avatarUrl,
            )
        }
        runCatching { repository.subscribeAll(entities) }
            .onFailure { Log.w(TAG, "Batch subscribe failed", it) }

        if (pushToAccount && entities.isNotEmpty()) {
            val actions = AccountActions(context)
            if (actions.canWriteBack()) {
                var done = 0
                for (channel in entities) {
                    done++
                    if (done % 25 == 0) onProgress(done, entities.size)
                    // Best-effort; a remote failure never aborts the import.
                    runCatching { actions.setSubscribed(channel.channelId, true) }
                }
            }
        }
        return entities.size
    }

    /**
     * Channels to export: local store merged with the signed-in account's REAL
     * subscriptions (account rows win — fresher names/avatars). Signed-out
     * exports fall back to the local store only.
     */
    suspend fun collectExportChannels(context: Context): List<ChannelSubscription> {
        val repository = SubscriptionRepository.getInstance(context)
        val local = runCatching { repository.getAllSubscriptions().firstOrNull().orEmpty() }
            .getOrDefault(emptyList())
        val actions = AccountActions(context)
        if (!actions.canWriteBack()) return local
        return runCatching {
            val crawl = com.omersusin.pitube.innertube.YouTube.webSubscribedChannels().getOrNull()
            if (crawl == null || crawl.channels.isEmpty()) return local
            val byId = local.associateBy { it.channelId }.toMutableMap()
            crawl.channels.forEach { remote ->
                byId[remote.id] = ChannelSubscription(
                    channelId = remote.id,
                    channelName = remote.name.ifBlank { byId[remote.id]?.channelName ?: remote.id },
                    channelThumbnail = remote.thumbnail.ifBlank { byId[remote.id]?.channelThumbnail.orEmpty() },
                )
            }
            byId.values.toList()
        }.getOrDefault(local)
    }

    fun buildNewPipeJson(channels: List<ChannelSubscription>): String {        val array = JSONArray()
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
