package com.omersusin.pitube.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AccountFetcher {
    private const val TAG = "AccountFetcher"
    private const val API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val CLIENT_VERSION = "2.20260114.08.00"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    data class AccountInfo(
        val name: String,
        val avatarUrl: String?,
        val handle: String?,
        val channelId: String? = null,
        val datasyncId: String? = null,
        val profileId: String? = null
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val profileManager by lazy { ProfileManager(com.omersusin.pitube.PiTubeApplication.instance) }

    fun getCached(context: Context): AccountInfo? {
        val profile = profileManager.active()
        if (profile.kind == ProfileKind.LOCAL) return null
        val name = profile.name
        if (name.isBlank() || name == ProfileManager.DEFAULT_LOCAL_NAME) return null
        return AccountInfo(
            name = name,
            avatarUrl = profile.avatarUrl,
            handle = profile.handle,
            channelId = null,
            datasyncId = profile.datasyncId,
            profileId = profile.id
        )
    }

    fun cache(context: Context, info: AccountInfo?) {
        if (info == null) return
        val profileId = info.profileId ?: profileManager.active().id
        profileManager.updateIdentity(
            id = profileId,
            name = info.name,
            handle = info.handle,
            avatarUrl = info.avatarUrl,
            datasyncId = info.datasyncId
        )
    }

    fun clearCache(context: Context) {
        val profile = profileManager.active()
        profileManager.replaceWithFreshLocal(profile.id)
    }

    suspend fun fetch(context: Context): AccountInfo? = withContext(Dispatchers.IO) {
        try {
            val profile = profileManager.active()
            val rawCookies = profileManager.cookiesFor(profile.id)
                ?: AuthManager.getRawCookies(context)
            if (rawCookies.isBlank()) {
                Log.w(TAG, "No cookies available")
                return@withContext null
            }

            val authHeader = KodaAuth.authHeader(rawCookies)

            val body = """{"context":{"client":{"clientName":"WEB","clientVersion":"$CLIENT_VERSION","hl":"en","gl":"US"}}}"""
            val reqBuilder = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/account/account_menu?key=$API_KEY")
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Cookie", rawCookies)
                .addHeader("User-Agent", UA)
                .addHeader("X-YouTube-Client-Name", "1")
                .addHeader("X-YouTube-Client-Version", CLIENT_VERSION)
                .addHeader("Origin", "https://www.youtube.com")
                .addHeader("X-Goog-AuthUser", "0")
            if (authHeader != null) reqBuilder.addHeader("Authorization", authHeader)

            Log.d(TAG, "Fetching account info from account_menu...")
            val resp = client.newCall(reqBuilder.build()).execute()
            resp.use { r ->
                KodaAuth.refreshFromResponse(context, r)
                if (!r.isSuccessful) {
                    Log.w(TAG, "HTTP ${r.code}")
                    return@withContext null
                }
                val bodyStr = r.body?.string() ?: return@withContext null
                Log.d(TAG, "Response length: ${bodyStr.length}")

                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    val preview = bodyStr.take(2000)
                    Log.d(TAG, "Raw response preview: $preview")
                }

                val json = JSONObject(bodyStr)

                var name: String? = null
                var photo: String? = null
                var handle: String? = null
                var channelId: String? = null
                var datasyncId: String? = null

                datasyncId = json.optJSONObject("responseContext")
                    ?.optJSONObject("mainAppWebResponseContext")
                    ?.optString("datasyncId", null)
                if (datasyncId.isNullOrBlank()) datasyncId = null
                Log.d(TAG, "datasyncId: $datasyncId")

                val actions = json.optJSONArray("actions")
                if (actions != null) {
                    Log.d(TAG, "Trying primary path: actions[0]...")
                    val item = actions.optJSONObject(0)
                        ?.optJSONObject("openPopupAction")
                        ?.optJSONObject("popup")
                        ?.optJSONObject("multiPageMenuRenderer")
                        ?.optJSONArray("sections")
                        ?.optJSONObject(0)
                        ?.optJSONObject("accountSectionListRenderer")
                        ?.optJSONArray("contents")
                        ?.optJSONObject(0)
                        ?.optJSONObject("accountItem")
                    if (item != null) {
                        Log.d(TAG, "Found accountItem in actions path")
                        name = item.optString("accountName", null)
                        photo = extractAvatar(item)
                        handle = extractHandle(item)
                        channelId = extractChannelId(item)
                    } else {
                        Log.d(TAG, "accountItem not found in actions path")
                    }
                }

                if (name == null) {
                    Log.d(TAG, "Trying fallback: header path...")
                    val header = json.optJSONObject("header")
                        ?.optJSONObject("accountSectionListRenderer")
                        ?.optJSONArray("contents")
                        ?.optJSONObject(0)
                        ?.optJSONObject("accountItem")
                    if (header != null) {
                        Log.d(TAG, "Found accountItem in header path")
                        name = header.optString("accountName", null)
                        photo = extractAvatar(header)
                        handle = extractHandle(header)
                        channelId = extractChannelId(header)
                    } else {
                        Log.d(TAG, "accountItem not found in header path")
                    }
                }

                if (name == null) {
                    Log.d(TAG, "Trying fallback: flat fields...")
                    name = json.optString("name", null)
                    photo = json.optString("photoUrl", null)
                }

                if (handle.isNullOrBlank()) {
                    Log.d(TAG, "Trying fallback: findHandleInJson...")
                    handle = findHandleInJson(json)
                }

                if (photo.isNullOrBlank()) {
                    Log.d(TAG, "Trying fallback: findAvatarInJson...")
                    photo = findAvatarInJson(json)
                }

                if (name.isNullOrBlank()) {
                    Log.w(TAG, "No account name found in response")
                    Log.d(TAG, "Full response keys: ${json.keys().asSequence().toList()}")
                    return@withContext null
                }

                Log.d(TAG, "Fetched account: name=$name, handle=$handle, channelId=$channelId, datasyncId=$datasyncId, avatar=${photo?.take(80)}")

                val profileId = profile.id
                if (profile.kind == ProfileKind.LOCAL && datasyncId != null) {
                    profileManager.addYouTubeProfile(
                        cookies = rawCookies,
                        name = name,
                        handle = handle,
                        avatarUrl = photo,
                        datasyncId = datasyncId
                    )
                } else {
                    profileManager.updateIdentity(
                        id = profileId,
                        name = name,
                        handle = handle,
                        avatarUrl = photo,
                        datasyncId = datasyncId
                    )
                }

                val info = AccountInfo(name, photo, handle, channelId, datasyncId, profileId)
                info
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch account: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun extractAvatar(item: JSONObject): String? {
        val photo = item.optString("accountPhoto", null)
        if (!photo.isNullOrBlank()) {
            Log.d(TAG, "  avatar: accountPhoto=$photo")
            return photo
        }
        val avatar = item.optJSONObject("avatar")
        if (avatar != null) {
            val photoUrl = avatar.optString("photo", null)
            if (!photoUrl.isNullOrBlank()) {
                Log.d(TAG, "  avatar: avatar.photo=$photoUrl")
                return photoUrl
            }
        }
        val thumbnail = item.optJSONObject("thumbnail")
        if (thumbnail != null) {
            val thumbnails = thumbnail.optJSONArray("thumbnails")
            if (thumbnails != null && thumbnails.length() > 0) {
                val url = thumbnails.optJSONObject(0)?.optString("url", null)
                Log.d(TAG, "  avatar: thumbnail[0]=$url")
                return url
            }
        }
        Log.d(TAG, "  avatar: not found")
        return null
    }

    private fun extractHandle(item: JSONObject): String? {
        val handle = item.optString("accountHandle", null)
        if (!handle.isNullOrBlank()) {
            Log.d(TAG, "  handle: accountHandle=$handle")
            return handle
        }
        val channelHandle = item.optString("channelHandle", null)
        if (!channelHandle.isNullOrBlank()) {
            Log.d(TAG, "  handle: channelHandle=$channelHandle")
            return channelHandle
        }
        val handleText = item.optString("handleText", null)
        if (!handleText.isNullOrBlank()) {
            Log.d(TAG, "  handle: handleText=$handleText")
            return handleText
        }
        val accountHandleText = item.optString("accountHandleText", null)
        if (!accountHandleText.isNullOrBlank()) {
            Log.d(TAG, "  handle: accountHandleText=$accountHandleText")
            return accountHandleText
        }
        Log.d(TAG, "  handle: not found")
        return null
    }

    private fun extractChannelId(item: JSONObject): String? {
        val channelId = item.optString("channelId", null)
        if (!channelId.isNullOrBlank()) {
            Log.d(TAG, "  channelId: direct=$channelId")
            return channelId
        }
        val navEndpoint = item.optJSONObject("navigationEndpoint")
        if (navEndpoint != null) {
            val browseId = navEndpoint.optJSONObject("browseEndpoint")?.optString("browseId", null)
            if (!browseId.isNullOrBlank()) {
                Log.d(TAG, "  channelId: browseEndpoint=$browseId")
                return browseId
            }
        }
        Log.d(TAG, "  channelId: not found")
        return null
    }

    private fun findHandleInJson(json: JSONObject): String? {
        val paths = listOf(
            "header" to "accountChannelHandle",
            "header" to "handle",
            "header" to "handleText",
        )
        for ((parent, field) in paths) {
            val value = json.optJSONObject(parent)?.optString(field, null)
            if (!value.isNullOrBlank()) {
                Log.d(TAG, "  handle fallback: $parent.$field=$value")
                return value
            }
        }
        val sections = json.optJSONObject("header")
            ?.optJSONObject("accountSectionListRenderer")
            ?.optJSONArray("contents")
        if (sections != null) {
            for (i in 0 until sections.length()) {
                val section = sections.optJSONObject(i)
                val accountItem = section?.optJSONObject("accountItem")
                if (accountItem != null) {
                    val h = accountItem.optString("accountHandle", null)
                    if (!h.isNullOrBlank()) {
                        Log.d(TAG, "  handle fallback: sections[$i].accountHandle=$h")
                        return h
                    }
                    val ch = accountItem.optString("channelHandle", null)
                    if (!ch.isNullOrBlank()) {
                        Log.d(TAG, "  handle fallback: sections[$i].channelHandle=$ch")
                        return ch
                    }
                    val ht = accountItem.optString("handleText", null)
                    if (!ht.isNullOrBlank()) {
                        Log.d(TAG, "  handle fallback: sections[$i].handleText=$ht")
                        return ht
                    }
                }
            }
        }
        return null
    }

    private fun findAvatarInJson(json: JSONObject): String? {
        val sections = json.optJSONObject("header")
            ?.optJSONObject("accountSectionListRenderer")
            ?.optJSONArray("contents")
        if (sections != null) {
            for (i in 0 until sections.length()) {
                val section = sections.optJSONObject(i)
                val accountItem = section?.optJSONObject("accountItem")
                if (accountItem != null) {
                    val photo = accountItem.optString("accountPhoto", null)
                    if (!photo.isNullOrBlank()) {
                        Log.d(TAG, "  avatar fallback: sections[$i].accountPhoto=$photo")
                        return photo
                    }
                    val avatar = accountItem.optJSONObject("avatar")
                    if (avatar != null) {
                        val photoUrl = avatar.optString("photo", null)
                        if (!photoUrl.isNullOrBlank()) {
                            Log.d(TAG, "  avatar fallback: sections[$i].avatar.photo=$photoUrl")
                            return photoUrl
                        }
                    }
                }
            }
        }
        return null
    }
}
