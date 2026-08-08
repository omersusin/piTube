package com.omersusin.pitube.data

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ProfileKind { YOUTUBE, LOCAL }

data class Profile(
    val id: String,
    val kind: ProfileKind,
    val name: String,
    val handle: String? = null,
    val avatarUrl: String? = null,
    val datasyncId: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val expired: Boolean = false
) {
    val isLocal: Boolean get() = kind == ProfileKind.LOCAL
}

class ProfileManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = sharedPrefs(appContext)

    init {
        synchronized(LOCK) {
            if (sharedProfiles == null) {
                migrateIfNeeded()
                sharedProfiles = MutableStateFlow(loadProfiles())
                sharedActiveId = MutableStateFlow(loadActiveId())
            }
        }
    }

    val profiles: StateFlow<List<Profile>> get() = sharedProfiles!!.asStateFlow()
    val activeProfileId: StateFlow<String> get() = sharedActiveId!!.asStateFlow()

    fun active(): Profile = sharedProfiles!!.value.firstOrNull { it.id == sharedActiveId!!.value }
        ?: ensureDefaultLocal()

    fun get(id: String): Profile? = sharedProfiles!!.value.firstOrNull { it.id == id }

    fun cookiesFor(id: String): String? =
        prefs.getString(keyCookies(id), null)?.takeIf { it.isNotBlank() }

    fun saveCookiesFor(id: String, cookies: String) {
        prefs.edit().putString(keyCookies(id), cookies).apply()
    }

    fun addYouTubeProfile(
        cookies: String,
        name: String? = null,
        handle: String? = null,
        avatarUrl: String? = null,
        datasyncId: String? = null
    ): Profile {
        val existing = datasyncId?.let { sync ->
            sharedProfiles!!.value.firstOrNull { it.datasyncId == sync }
        }
        val profile = existing?.copy(
            name = name ?: existing.name,
            handle = handle ?: existing.handle,
            avatarUrl = avatarUrl ?: existing.avatarUrl,
            expired = false
        ) ?: Profile(
            id = UUID.randomUUID().toString(),
            kind = ProfileKind.YOUTUBE,
            name = name ?: "YouTube account",
            handle = handle,
            avatarUrl = avatarUrl,
            datasyncId = datasyncId
        )
        saveCookiesFor(profile.id, cookies)
        upsert(profile)
        return profile
    }

    fun addLocalProfile(name: String): Profile {
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            kind = ProfileKind.LOCAL,
            name = name.trim().takeIf { it.isNotBlank() } ?: "Local profile"
        )
        upsert(profile)
        return profile
    }

    fun updateIdentity(
        id: String,
        name: String? = null,
        handle: String? = null,
        avatarUrl: String? = null,
        datasyncId: String? = null
    ) {
        val current = get(id) ?: return
        upsert(
            current.copy(
                name = name?.takeIf { it.isNotBlank() } ?: current.name,
                handle = handle ?: current.handle,
                avatarUrl = avatarUrl ?: current.avatarUrl,
                datasyncId = datasyncId ?: current.datasyncId
            )
        )
    }

    fun setExpired(id: String, expired: Boolean) {
        val current = get(id) ?: return
        if (current.expired == expired) return
        upsert(current.copy(expired = expired))
    }

    fun remove(id: String): Boolean {
        val list = sharedProfiles!!.value
        if (list.size <= 1) return false
        val target = list.firstOrNull { it.id == id } ?: return false
        prefs.edit().remove(keyCookies(id)).apply()
        val next = list.filterNot { it.id == id }
        saveProfiles(next)
        if (sharedActiveId!!.value == target.id) {
            setActive(next.first().id)
        }
        return true
    }

    fun replaceWithFreshLocal(id: String) {
        val current = get(id) ?: return
        prefs.edit().remove(keyCookies(id)).apply()
        upsert(
            current.copy(
                kind = ProfileKind.LOCAL,
                name = DEFAULT_LOCAL_NAME,
                handle = null,
                avatarUrl = null,
                datasyncId = null,
                expired = false
            )
        )
    }

    fun setActive(id: String) {
        if (get(id) == null) return
        val leaving = sharedActiveId!!.value
        if (leaving.isNotBlank() && leaving != id) sharedPreviousId.value = leaving
        prefs.edit().putString(KEY_ACTIVE_PROFILE, id).apply()
        sharedActiveId!!.value = id
    }

    val previousProfileId: StateFlow<String?> get() = sharedPreviousId.asStateFlow()

    private fun upsert(profile: Profile) {
        val list = sharedProfiles!!.value
        val next = if (list.any { it.id == profile.id }) {
            list.map { if (it.id == profile.id) profile else it }
        } else {
            list + profile
        }
        saveProfiles(next)
    }

    private fun ensureDefaultLocal(): Profile {
        val existing = sharedProfiles!!.value.firstOrNull()
        if (existing != null) {
            setActive(existing.id)
            return existing
        }
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            kind = ProfileKind.LOCAL,
            name = DEFAULT_LOCAL_NAME
        )
        saveProfiles(listOf(profile))
        setActive(profile.id)
        return profile
    }

    private fun migrateIfNeeded() {
        if (prefs.contains(KEY_PROFILES)) return
        val legacyCookies = prefs.getString(LEGACY_KEY_COOKIES, null)?.takeIf { it.isNotBlank() }
        val profile = if (legacyCookies != null) {
            Profile(
                id = UUID.randomUUID().toString(),
                kind = ProfileKind.YOUTUBE,
                name = prefs.getString(LEGACY_KEY_USER_NAME, null)?.takeIf { it.isNotBlank() }
                    ?: "YouTube account",
                avatarUrl = prefs.getString(LEGACY_KEY_USER_AVATAR, null)?.takeIf { it.isNotBlank() }
            )
        } else {
            Profile(
                id = UUID.randomUUID().toString(),
                kind = ProfileKind.LOCAL,
                name = DEFAULT_LOCAL_NAME
            )
        }
        val editor = prefs.edit()
        editor.putString(KEY_PROFILES, JSONArray().put(profile.toJson()).toString())
        editor.putString(KEY_ACTIVE_PROFILE, profile.id)
        editor.putString(KEY_MIGRATED_LEGACY_ID, profile.id)
        if (legacyCookies != null) editor.putString(keyCookies(profile.id), legacyCookies)
        editor.apply()
    }

    private fun saveProfiles(list: List<Profile>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
        sharedProfiles!!.value = list
    }

    private fun loadProfiles(): List<Profile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { fromJson(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load profiles", e)
            emptyList()
        }
    }

    private fun loadActiveId(): String {
        val stored = prefs.getString(KEY_ACTIVE_PROFILE, null)
        val list = loadProfiles()
        return stored?.takeIf { id -> list.any { it.id == id } }
            ?: list.firstOrNull()?.id
            ?: ""
    }

    private fun Profile.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind.name)
        put("name", name)
        put("handle", handle ?: JSONObject.NULL)
        put("avatarUrl", avatarUrl ?: JSONObject.NULL)
        put("datasyncId", datasyncId ?: JSONObject.NULL)
        put("addedAt", addedAt)
        put("expired", expired)
    }

    private fun fromJson(obj: JSONObject): Profile? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        fun str(key: String) = obj.optString(key).takeIf { it.isNotBlank() && it != "null" }
        return Profile(
            id = id,
            kind = if (obj.optString("kind") == ProfileKind.LOCAL.name) ProfileKind.LOCAL
            else ProfileKind.YOUTUBE,
            name = str("name") ?: "Profile",
            handle = str("handle"),
            avatarUrl = str("avatarUrl"),
            datasyncId = str("datasyncId"),
            addedAt = obj.optLong("addedAt", 0L),
            expired = obj.optBoolean("expired", false)
        )
    }

    companion object {
        private const val TAG = "ProfileManager"
        private const val PREFS_FILE_NAME = "piTube_profiles"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE_PROFILE = "active_profile"
        private const val KEY_MIGRATED_LEGACY_ID = "migrated_legacy_profile"
        private const val LEGACY_KEY_COOKIES = "raw_cookies"
        private const val LEGACY_KEY_USER_NAME = "user_name"
        private const val LEGACY_KEY_USER_AVATAR = "user_avatar"
        const val DEFAULT_LOCAL_NAME = "No account"
        private fun keyCookies(id: String) = "cookies_$id"

        private fun buildPrefs(context: Context) = EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        @Volatile
        private var prefsInstance: android.content.SharedPreferences? = null

        fun sharedPrefs(context: Context): android.content.SharedPreferences {
            prefsInstance?.let { return it }
            return synchronized(LOCK) {
                prefsInstance ?: run {
                    val app = context.applicationContext
                    val created = try {
                        buildPrefs(app)
                    } catch (e: Exception) {
                        Log.e(TAG, "EncryptedSharedPreferences corrupted, resetting", e)
                        app.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
                            .edit().clear().apply()
                        java.io.File(
                            app.filesDir.parent, "shared_prefs/$PREFS_FILE_NAME.xml"
                        ).delete()
                        buildPrefs(app)
                    }
                    prefsInstance = created
                    created
                }
            }
        }

        fun legacyProfileId(context: Context): String? =
            sharedPrefs(context).getString(KEY_MIGRATED_LEGACY_ID, null)

        fun activeProfileId(context: Context): String =
            sharedPrefs(context).getString(KEY_ACTIVE_PROFILE, null).orEmpty()

        fun profileScopedKey(base: String, profileId: String, legacyProfileId: String?): String =
            if (profileId == legacyProfileId) base else "${base}_$profileId"

        private val LOCK = Any()
        @Volatile
        private var sharedProfiles: MutableStateFlow<List<Profile>>? = null
        @Volatile
        private var sharedActiveId: MutableStateFlow<String>? = null
        private val sharedPreviousId = MutableStateFlow<String?>(null)
    }
}
