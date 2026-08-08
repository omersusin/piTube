package com.omersusin.pitube.data

import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

data class ImportedChannel(
    val channelId: String?,
    val name: String,
    val unresolvedPath: String? = null,
    val avatarUrl: String? = null
)

data class ImportedFile(
    val channels: List<ImportedChannel> = emptyList(),
    val foreignServiceEntries: Int = 0
)

object SubscriptionTransfer {
    private const val SERVICE_ID_YOUTUBE = 0
    private val SQLITE_MAGIC = "SQLite format 3".toByteArray(Charsets.US_ASCII) + 0.toByte()
    private const val MAX_DB_BYTES = 128L * 1024 * 1024
    private val UC_ID = Regex("""^UC[\w-]{20,}$""")
    private val CHANNEL_ID_IN_URL = Regex("""(?:channel/|channel_id=|/c(?:hannel)?/)(UC[\w-]{20,})""")
    private val HANDLE_IN_URL = Regex("""youtube\.com/@([\w.\-]+)""", RegexOption.IGNORE_CASE)
    private val VANITY_IN_URL = Regex("""youtube\.com/(c|user)/([\w.\-]+)""", RegexOption.IGNORE_CASE)
    private val OUTLINE = Regex("""<outline\s+([^>]*?)/?>""", RegexOption.IGNORE_CASE)

    fun read(bytes: ByteArray, scratchFile: File): ImportedFile {
        if (looksLikeZip(bytes)) {
            return if (unpackDatabase(bytes, scratchFile)) {
                try { parseDatabase(scratchFile) } finally { scratchFile.delete() }
            } else ImportedFile()
        }
        if (looksLikeSqlite(bytes)) {
            return try {
                scratchFile.writeBytes(bytes)
                parseDatabase(scratchFile)
            } catch (e: Exception) { ImportedFile() } finally { scratchFile.delete() }
        }
        val text = bytes.toString(Charsets.UTF_8)
        return ImportedFile(
            channels = parse(text),
            foreignServiceEntries = countForeignServiceEntries(text)
        )
    }

    fun parse(text: String): List<ImportedChannel> {
        val trimmed = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        return when {
            trimmed.startsWith("{") || trimmed.startsWith("[") -> parseNewPipeJson(trimmed)
            trimmed.startsWith("<") -> parseOpml(trimmed)
            else -> parseCsv(trimmed)
        }
    }

    fun countForeignServiceEntries(text: String): Int {
        val trimmed = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return 0
        return try {
            subscriptionArray(trimmed)?.let { array ->
                (0 until array.length()).count { i ->
                    val obj = array.optJSONObject(i) ?: return@count false
                    obj.has("service_id") && obj.optInt("service_id", SERVICE_ID_YOUTUBE) != SERVICE_ID_YOUTUBE
                }
            } ?: 0
        } catch (e: Exception) { 0 }
    }

    private fun looksLikeZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

    private fun looksLikeSqlite(bytes: ByteArray): Boolean =
        bytes.size >= SQLITE_MAGIC.size && SQLITE_MAGIC.indices.all { bytes[it] == SQLITE_MAGIC[it] }

    private fun unpackDatabase(bytes: ByteArray, dest: File): Boolean {
        return try {
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.substringAfterLast('/')
                    if (entry.isDirectory || !name.endsWith(".db", ignoreCase = true)) {
                        zip.closeEntry(); continue
                    }
                    dest.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            val read = zip.read(buffer)
                            if (read <= 0) break
                            written += read
                            if (written > MAX_DB_BYTES) throw IllegalStateException("Too large")
                            out.write(buffer, 0, read)
                        }
                    }
                    return true
                }
            }
            false
        } catch (e: Exception) { dest.delete(); false }
    }

    private fun parseDatabase(file: File): ImportedFile {
        return try {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val channels = mutableListOf<ImportedChannel>()
                var foreign = 0
                db.rawQuery("SELECT service_id, url, name, avatar_url FROM subscriptions", null).use { cursor ->
                    val serviceCol = cursor.getColumnIndex("service_id")
                    val urlCol = cursor.getColumnIndex("url")
                    val nameCol = cursor.getColumnIndex("name")
                    val avatarCol = cursor.getColumnIndex("avatar_url")
                    while (cursor.moveToNext()) {
                        val service = if (serviceCol >= 0) cursor.getInt(serviceCol) else SERVICE_ID_YOUTUBE
                        if (service != SERVICE_ID_YOUTUBE) { foreign++; continue }
                        val url = if (urlCol >= 0) cursor.getString(urlCol) else null
                        if (url.isNullOrBlank()) continue
                        val name = if (nameCol >= 0) cursor.getString(nameCol)?.takeIf { it.isNotBlank() } else null
                        val avatar = if (avatarCol >= 0) cursor.getString(avatarCol)?.takeIf { it.isNotBlank() } else null
                        val channel = fromUrl(url, name)?.copy(avatarUrl = avatar) ?: continue
                        channels.add(channel)
                    }
                }
                ImportedFile(channels, foreign)
            }
        } catch (e: Exception) { ImportedFile() }
    }

    private fun subscriptionArray(text: String): JSONArray? {
        if (text.startsWith("[")) return JSONArray(text)
        val root = JSONObject(text)
        return root.optJSONArray("subscriptions")
    }

    fun parseNewPipeJson(text: String): List<ImportedChannel> {
        return try {
            val array = subscriptionArray(text) ?: return emptyList()
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                if (obj.optInt("service_id", SERVICE_ID_YOUTUBE) != SERVICE_ID_YOUTUBE) return@mapNotNull null
                val url = obj.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = obj.optString("name").takeIf { it.isNotBlank() }
                fromUrl(url, name)
            }
        } catch (e: Exception) { emptyList() }
    }

    fun parseCsv(text: String): List<ImportedChannel> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyList()
        val header = splitCsvLine(lines.first()).map { it.trim().lowercase() }
        val looksLikeHeader = header.any { it.contains("channel") }
        var idIdx = header.indexOfFirst { it.contains("id") }
        var urlIdx = header.indexOfFirst { it.contains("url") }
        var nameIdx = header.indexOfFirst { it.contains("title") || it.contains("name") }
        if (!looksLikeHeader) { idIdx = 0; urlIdx = 1; nameIdx = 2 }
        val rows = if (looksLikeHeader) lines.drop(1) else lines
        return rows.mapNotNull { line ->
            val cells = splitCsvLine(line)
            fun cell(index: Int): String? = cells.getOrNull(index)?.trim()?.takeIf { it.isNotBlank() }
            val name = cell(nameIdx)
            val id = cell(idIdx)?.takeIf { it.startsWith("UC") && it.length >= 20 }
            if (id != null) return@mapNotNull ImportedChannel(id, name ?: id)
            val url = cell(urlIdx) ?: return@mapNotNull null
            fromUrl(url, name)
        }
    }

    private fun splitCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { current.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { cells.add(current.toString()); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        cells.add(current.toString())
        return cells
    }

    fun parseOpml(text: String): List<ImportedChannel> {
        return OUTLINE.findAll(text).mapNotNull { match ->
            val attrs = match.groupValues[1]
            val name = attribute(attrs, "text") ?: attribute(attrs, "title")
            val url = attribute(attrs, "xmlUrl") ?: attribute(attrs, "xmlurl")
                ?: attribute(attrs, "htmlUrl") ?: attribute(attrs, "htmlurl")
                ?: return@mapNotNull null
            fromUrl(unescapeXml(url), name?.let { unescapeXml(it) })
        }.toList()
    }

    private fun attribute(attrs: String, name: String): String? {
        val quote = '"'
        val pattern = "b" + Regex.escape(name) + "s*=s*$quote([^$quote]*)$quote"
        return Regex(pattern).find(attrs)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }

    private fun unescapeXml(value: String): String = value
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")

    fun fromUrl(url: String, name: String?): ImportedChannel? {
        val cleaned = url.trim().removeSuffix("/")
        if (cleaned.isBlank()) return null
        CHANNEL_ID_IN_URL.find(cleaned)?.let { match ->
            val id = match.groupValues[1]
            return ImportedChannel(id, name ?: id)
        }
        if (UC_ID.matches(cleaned)) return ImportedChannel(cleaned, name ?: cleaned)
        HANDLE_IN_URL.find(cleaned)?.let { match ->
            val handle = "@" + match.groupValues[1]
            return ImportedChannel(null, name ?: handle, unresolvedPath = handle)
        }
        VANITY_IN_URL.find(cleaned)?.let { match ->
            val path = match.groupValues[1] + "/" + match.groupValues[2]
            return ImportedChannel(null, name ?: match.groupValues[2], unresolvedPath = path)
        }
        if (cleaned.startsWith("@") && cleaned.length > 1 && !cleaned.contains('/')) {
            return ImportedChannel(null, name ?: cleaned, unresolvedPath = cleaned)
        }
        return null
    }

    fun buildExportJson(channels: List<Pair<String, String>>, appVersion: String): String {
        val root = JSONObject()
        root.put("app_version", appVersion)
        root.put("app_version_int", 0)
        val array = JSONArray()
        channels.forEach { (id, name) ->
            array.put(JSONObject().apply {
                put("service_id", SERVICE_ID_YOUTUBE)
                put("url", "https://www.youtube.com/channel/$id")
                put("name", name)
            })
        }
        root.put("subscriptions", array)
        return root.toString(2)
    }
}
