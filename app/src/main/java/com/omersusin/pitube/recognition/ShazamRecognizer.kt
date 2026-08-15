package com.omersusin.pitube.recognition

import com.google.gson.JsonParser
import com.omersusin.pitube.data.local.RecognitionFailureType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Shazam discovery-endpoint recognizer. Direct Kotlin port of the GPL-3.0
 * implementation in rukamori/ArchiveTune and vivizzz007/vivi-music's
 * `shazamkit` (which in turn follows Audile's request shape): a POST against
 * `amp.shazam.com/discovery/v5/.../tag/{uuid}/{uuid}` carrying a
 * `data:audio/vnd.shazam.sig;base64,...` signature. No API key required.
 */
object ShazamRecognizer {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    private val userAgents =
        listOf(
            "Dalvik/2.1.0 (Linux; U; Android 5.0.2; VS980 4G Build/LRX22G)",
            "Dalvik/1.6.0 (Linux; U; Android 4.4.2; SM-T210 Build/KOT49H)",
            "Dalvik/2.1.0 (Linux; U; Android 5.1.1; SM-P905V Build/LMY47X)",
            "Dalvik/2.1.0 (Linux; U; Android 6.0.1; SM-G920F Build/MMB29K)",
            "Dalvik/2.1.0 (Linux; U; Android 5.0; SM-G900F Build/LRX21T)",
            "Dalvik/2.1.0 (Linux; U; Android 7.0; SM-G930F Build/NRD90M)",
        )

    private val timezones =
        listOf(
            "Europe/Paris",
            "Europe/London",
            "America/New_York",
            "America/Los_Angeles",
            "Asia/Tokyo",
            "Asia/Dubai",
        )

    /**
     * @param signatureUri `data:audio/vnd.shazam.sig;base64,...` from
     *   [ShazamSignatureGenerator].
     * @param sampleDurationMs declared duration of the signature.
     * @throws RecognitionException NO_MATCH when no track is found, BAD_CONNECTION
     *   for transport failures, OTHER for rate limiting / parsing issues.
     */
    suspend fun recognize(
        signatureUri: String,
        sampleDurationMs: Long,
    ): TrackMatch = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis() / 1000
        val uuid1 = UUID.randomUUID().toString().uppercase()
        val uuid2 = UUID.randomUUID().toString()

        val body =
            """
            {
              "geolocation": {
                "altitude": ${Random.nextDouble() * 400 + 100},
                "latitude": ${Random.nextDouble() * 180 - 90},
                "longitude": ${Random.nextDouble() * 360 - 180}
              },
              "signature": {
                "samplems": $sampleDurationMs,
                "timestamp": $timestamp,
                "uri": "$signatureUri"
              },
              "timestamp": $timestamp,
              "timezone": "${timezones.random()}"
            }
            """.trimIndent()

        val request =
            Request.Builder()
                .url(
                    "https://amp.shazam.com/discovery/v5/en/US/android/-/tag/$uuid1/$uuid2" +
                        "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&video=v3",
                )
                .header("User-Agent", userAgents.random())
                .header("Content-Language", "en_US")
                .header("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    404 -> throw RecognitionException(RecognitionFailureType.NO_MATCH, "No match found")
                    429 -> throw RecognitionException(RecognitionFailureType.OTHER, "Rate limit exceeded. Please try again later.")
                    in 500..599 -> throw RecognitionException(RecognitionFailureType.BAD_CONNECTION, "Shazam service temporarily unavailable")
                }
                if (!response.isSuccessful) {
                    throw RecognitionException(RecognitionFailureType.OTHER, "Recognition failed (HTTP ${response.code})")
                }

                parseTrack(response.body?.string().orEmpty())
                    ?: throw RecognitionException(RecognitionFailureType.NO_MATCH, "No match found")
            }
        } catch (e: RecognitionException) {
            throw e
        } catch (e: java.io.IOException) {
            throw RecognitionException(RecognitionFailureType.BAD_CONNECTION, "Network unavailable")
        }
    }

    /**
     * Maps the discovery response to [TrackMatch]; null when the response has
     * no track (or a track without a usable title/subtitle — Audile's
     * convention for "no match").
     */
    fun parseTrack(body: String): TrackMatch? {
        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return null
        val track = root.getAsJsonObject("track") ?: return null
        val title = track.get("title")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        val artist = track.get("subtitle")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        if (title.isBlank() && artist.isBlank()) return null

        val songSection = track.getAsJsonArray("sections")?.firstOrNull {
            it.asJsonObject.get("type")?.takeIf { !it.isJsonNull }?.asString == "SONG"
        }?.asJsonObject
        val metadata = songSection?.getAsJsonArray("metadata")
        fun meta(titleKey: String): String? =
            metadata?.firstOrNull {
                it.asJsonObject.get("title")?.takeIf { !it.isJsonNull }?.asString == titleKey
            }?.asJsonObject?.get("text")?.takeIf { !it.isJsonNull }?.asString

        val hub = track.getAsJsonObject("hub")
        val appleAction =
            hub?.getAsJsonArray("options")?.firstOrNull {
                it.asJsonObject.get("providername")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                    .contains("apple", ignoreCase = true)
            }?.asJsonObject?.getAsJsonArray("actions")?.firstOrNull()
        val spotifyProvider =
            hub?.getAsJsonArray("providers")?.firstOrNull {
                it.asJsonObject.get("caption")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                    .contains("spotify", ignoreCase = true)
            }?.asJsonObject
        val youtubeAction =
            hub?.getAsJsonArray("options")?.firstOrNull {
                it.asJsonObject.get("type")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                    .contains("video", ignoreCase = true)
            }?.asJsonObject?.getAsJsonArray("actions")?.firstOrNull()

        val youtubeUri = youtubeAction?.asJsonObject?.get("uri")?.takeIf { !it.isJsonNull }?.asString
        val youtubeVideoId =
            youtubeUri?.let { uri ->
                uri.substringAfterLast("v=", "").takeIf { it.isNotEmpty() }
                    ?: uri.substringAfterLast("/", "").takeIf { it.isNotEmpty() && it.length == 11 }
            }

        return TrackMatch(
            trackId = track.get("key")?.takeIf { !it.isJsonNull }?.asString
                ?: root.get("tagid")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
            title = title,
            artist = artist,
            album = meta("Album"),
            genre = track.getAsJsonObject("genres")?.get("primary")?.takeIf { !it.isJsonNull }?.asString,
            releaseDate = meta("Released"),
            coverArtUrl = track.getAsJsonObject("images")?.get("coverarthq")?.takeIf { !it.isJsonNull }?.asString,
            isrc = track.get("isrc")?.takeIf { !it.isJsonNull }?.asString,
            youtubeVideoId = youtubeVideoId,
            appleMusicUrl = appleAction?.asJsonObject?.get("uri")?.takeIf { !it.isJsonNull }?.asString,
            spotifyUrl = spotifyProvider?.getAsJsonArray("actions")?.firstOrNull()?.asJsonObject
                ?.get("uri")?.takeIf { !it.isJsonNull }?.asString,
            sourceProvider = "Shazam",
        )
    }
}