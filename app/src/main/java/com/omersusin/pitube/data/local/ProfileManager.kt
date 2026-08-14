package com.omersusin.pitube.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** What kind of identity a profile is. */
enum class ProfileKind { YOUTUBE, LOCAL }

/**
 * One identity in the app.
 *
 * A [ProfileKind.YOUTUBE] profile has a stored cookie string and a Google
 * identity behind it. A [ProfileKind.LOCAL] profile has neither - it is a
 * device-only identity, which is a first-class thing here rather than a
 * consolation prize: this app is built to work fully signed out, so "signed
 * out" is simply the local profile that always exists.
 *
 * [id] is a device-local UUID and never changes, because it keys this
 * profile's stored cookies and its feed-shaping data. [datasyncId] is
 * YouTube's own account identifier (`responseContext.mainAppWebResponseContext
 * .datasyncId`) and is filled in once an authenticated call has answered; it
 * exists only to recognise that a re-added account is one already in the
 * roster, so adding it again updates rather than duplicates.
 */
data class Profile(
    val id: String,
    val kind: ProfileKind,
    val name: String,
    val handle: String? = null,
    val avatarUrl: String? = null,
    val email: String? = null,
    val datasyncId: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    /**
     * True once YouTube answered this profile's authenticated call as
     * anonymous. Per-profile on purpose: a single global flag would badge the
     * wrong row the moment there is more than one account.
     */
    val expired: Boolean = false,
) {
    val isLocal: Boolean get() = kind == ProfileKind.LOCAL
}

/**
 * The roster of profiles and which one is active.
 *
 * **Switching is deliberately just "point at another stored cookie string".**
 * Every consumer in this app resolves the session fresh on each call - the
 * runtime singleton shifts, and NewPipe's downloader builds its requests from
 * the same source - so redirecting the active profile is enough to move the
 * whole app onto another account. No re-authentication, no network, works
 * offline. What does need doing is invalidation, and that is [AccountSwitcher]'s
 * job.
 *
 * State is companion-scoped for the usual reason: with no DI, every ViewModel
 * news up its own instance, and a switch has to reach all of them at once.
 */
class ProfileManager(context: Context) {

    private val appContext = context.applicationContext

    // Opened once per process, not once per instance: SessionManager holds one
    // of these and is itself newed up in several places, and each
    // EncryptedSharedPreferences.create is a keystore round trip.
    private val prefs = sharedPrefs(appContext)

    init {
        synchronized(LOCK) {
            if (sharedProfiles == null) {
                sharedProfiles = MutableStateFlow(loadProfiles())
                sharedActiveId = MutableStateFlow(loadActiveId())
            }
        }
    }

    val profiles: StateFlow<List<Profile>> get() = sharedProfiles!!.asStateFlow()

    /**
     * The active profile's id. Everything that caches account-derived state
     * observes this and resets when it changes.
     */
    val activeProfileId: StateFlow<String> get() = sharedActiveId!!.asStateFlow()

    fun active(): Profile =
        sharedProfiles!!.value.firstOrNull { it.id == sharedActiveId!!.value }
            ?: ensureDefaultLocal()

    fun get(id: String): Profile? = sharedProfiles!!.value.firstOrNull { it.id == id }

    // ---------------- Cookies (per profile) ----------------

    fun cookiesFor(id: String): String? =
        prefs.getString(keyCookies(id), null)?.takeIf { it.isNotBlank() }

    fun saveCookiesFor(id: String, cookies: String) {
        prefs.edit().putString(keyCookies(id), cookies).apply()
    }

    // ---------------- Roster writes ----------------

    /**
     * Add (or refresh) a YouTube profile from a captured cookie string.
     *
     * When [datasyncId] matches a profile already in the roster this updates
     * that one in place and returns it, so re-signing into an account that is
     * already here repairs it instead of leaving two rows that look identical.
     */
    fun addYouTubeProfile(
        cookies: String,
        name: String? = null,
        handle: String? = null,
        avatarUrl: String? = null,
        email: String? = null,
        datasyncId: String? = null
    ): Profile {
        val existing = datasyncId?.let { sync ->
            sharedProfiles!!.value.firstOrNull { it.datasyncId == sync }
        }
        val profile = existing?.copy(
            name = name ?: existing.name,
            handle = handle ?: existing.handle,
            avatarUrl = avatarUrl ?: existing.avatarUrl,
            email = email ?: existing.email,
            expired = false,
        ) ?: Profile(
            id = UUID.randomUUID().toString(),
            kind = ProfileKind.YOUTUBE,
            name = name ?: "YouTube account",
            handle = handle,
            avatarUrl = avatarUrl,
            email = email,
            datasyncId = datasyncId
        )
        saveCookiesFor(profile.id, cookies)
        upsert(profile)
        return profile
    }

    /** Create a device-only profile. Needs no account and never makes a request. */
    fun addLocalProfile(name: String): Profile {
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            kind = ProfileKind.LOCAL,
            name = name.trim().takeIf { it.isNotBlank() } ?: "Local profile"
        )
        upsert(profile)
        return profile
    }

    /** Fill in identity details once an authenticated call has revealed them. */
    fun updateIdentity(
        id: String,
        name: String? = null,
        handle: String? = null,
        avatarUrl: String? = null,
        email: String? = null,
        datasyncId: String? = null
    ) {
        val current = get(id) ?: return
        upsert(
            current.copy(
                name = name?.takeIf { it.isNotBlank() } ?: current.name,
                handle = handle ?: current.handle,
                avatarUrl = avatarUrl ?: current.avatarUrl,
                email = email ?: current.email,
                datasyncId = datasyncId ?: current.datasyncId
            )
        )
    }

    fun setExpired(id: String, expired: Boolean) {
        val current = get(id) ?: return
        if (current.expired == expired) return
        upsert(current.copy(expired = expired))
    }

    /**
     * Remove a profile, its cookies and its feed-shaping data.
     *
     * The last remaining profile cannot be removed - there is always an
     * identity, even if it is only the default local one - and removing the
     * active profile falls back to another rather than leaving nothing active.
     */
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

    /**
     * Turn a profile into a device-only one: drop its cookies and its Google
     * identity, keep everything else.
     *
     * This is what signing out of the *last* remaining account does. The id is
     * deliberately kept, so any feed-shaping data scoped to it survives the
     * sign-out - those are device-local things the user built up when they did
     * not need an account.
     */
    fun replaceWithFreshLocal(id: String) {
        val current = get(id) ?: return
        prefs.edit().remove(keyCookies(id)).apply()
        upsert(
            current.copy(
                kind = ProfileKind.LOCAL,
                name = DEFAULT_LOCAL_NAME,
                handle = null,
                avatarUrl = null,
                email = null,
                datasyncId = null,
                expired = false,
            )
        )
    }

    /** Point the app at another profile. Invalidation is [AccountSwitcher]'s job. */
    fun setActive(id: String) {
        if (get(id) == null) return
        val leaving = sharedActiveId!!.value
        if (leaving.isNotBlank() && leaving != id) sharedPreviousId.value = leaving
        prefs.edit().putString(KEY_ACTIVE_PROFILE, id).apply()
        sharedActiveId!!.value = id
    }

    /**
     * The profile the user was on before this one, for the long-press
     * quick-toggle. In memory only: it describes what this session has been
     * doing, and a value restored from disk would send the first quick-toggle
     * after a restart somewhere the user did not just come from.
     */
    val previousProfileId: StateFlow<String?> get() = sharedPreviousId.asStateFlow()

    // ---------------- Legacy migration ----------------

    /**
     * Bring a pre-profiles install forward.
     *
     * An existing signed-in session in the DataStore mirror becomes the first
     * YouTube profile and keeps its stored name and avatar, so the upgrade is
     * invisible: the app comes back signed into the same account. An install
     * with no session gets the default local profile. Either way the existing
     * feed-shaping data belongs to whichever profile this produces - it is what
     * the user was looking at before the upgrade.
     *
     * Safe to call repeatedly: it only acts while there is no roster yet.
     */
    fun ensureMigrated(
        legacyCookies: String?,
        legacyName: String?,
        legacyAvatar: String?
    ) {
        synchronized(MIGRATION_LOCK) {
            if (sharedProfiles!!.value.isNotEmpty()) return
            val cookies = legacyCookies?.takeIf { it.isNotBlank() }
            val profile = if (cookies != null) {
                Profile(
                    id = UUID.randomUUID().toString(),
                    kind = ProfileKind.YOUTUBE,
                    name = legacyName?.takeIf { it.isNotBlank() } ?: "YouTube account",
                    avatarUrl = legacyAvatar?.takeIf { it.isNotBlank() }
                )
            } else {
                Profile(
                    id = UUID.randomUUID().toString(),
                    kind = ProfileKind.LOCAL,
                    name = DEFAULT_LOCAL_NAME
                )
            }
            val editor = prefs.edit()
            if (cookies != null) editor.putString(keyCookies(profile.id), cookies)
            editor.apply()
            saveProfiles(listOf(profile))
            setActive(profile.id)
        }
    }

    // ---------------- Internals ----------------

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
            android.util.Log.e(TAG, "Failed to load profiles", e)
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
        put("email", email ?: JSONObject.NULL)
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
            email = str("email"),
            datasyncId = str("datasyncId"),
            addedAt = obj.optLong("addedAt", 0L),
            expired = obj.optBoolean("expired", false),
        )
    }

    companion object {
        private const val TAG = "ProfileManager"
        private const val PREFS_FILE_NAME = "pitube_profiles"

        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE_PROFILE = "active_profile"

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

        /**
         * The one encrypted store, shared by every ProfileManager and
         * SessionManager in the process. Carries keystore-corruption recovery:
         * the encrypted file is wiped and rebuilt if the key becomes unusable.
         */
        fun sharedPrefs(context: Context): android.content.SharedPreferences {
            prefsInstance?.let { return it }
            return synchronized(LOCK) {
                prefsInstance ?: run {
                    val app = context.applicationContext
                    val created = try {
                        buildPrefs(app)
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "EncryptedSharedPreferences corrupted, resetting", e)
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

        private val LOCK = Any()
        private val MIGRATION_LOCK = Any()

        @Volatile
        private var sharedProfiles: MutableStateFlow<List<Profile>>? = null

        @Volatile
        private var sharedActiveId: MutableStateFlow<String>? = null

        private val sharedPreviousId = MutableStateFlow<String?>(null)
    }
}