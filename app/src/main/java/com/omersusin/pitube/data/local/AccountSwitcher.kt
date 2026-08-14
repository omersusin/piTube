package com.omersusin.pitube.data.local

import android.content.Context
import com.omersusin.pitube.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The one place that decides what switching profiles means.
 *
 * Pointing the app at another profile is the easy half - see [ProfileManager],
 * where it is a single preference write. The hard half is everything in the
 * process that is still holding the *previous* profile's state, and getting
 * that wrong is how an account switcher ends up showing one account's feed
 * under another account's name.
 *
 * What has to be dropped, and why:
 *
 * - **visitorData.** Cached in [YouTube]'s singleton and persisted device-wide,
 *   and prefetched on app start. It is the anti-bot identity and a stale or
 *   shared value gets flagged `LOGIN_REQUIRED`. Carrying one account's into
 *   another is exactly that failure, so it is dropped from memory and disk and
 *   re-minted.
 * - **The in-memory Home feed cache and the persisted Room feed cache**, which
 *   hold one account's personalised results.
 * - **The home-feed discovery rotation prefs**, which pin the current
 *   identity's discovery query order.
 *
 * Everything else follows automatically, because every consumer resolves the
 * runtime session ([YouTube.cookie]) fresh on each call.
 */
class AccountSwitcher(context: Context) {

    private val appContext = context.applicationContext
    private val profileManager = ProfileManager(appContext)
    private val sessionManager = SessionManager(appContext)

    val profiles: StateFlow<List<Profile>> get() = profileManager.profiles

    /** The active profile id. Every account-derived cache observes this. */
    val activeProfileId: StateFlow<String> get() = profileManager.activeProfileId

    fun active(): Profile = profileManager.active()

    /**
     * Re-point the runtime session at the active profile without switching.
     *
     * Used after a login screen temporarily cleared the shared session (adding
     * a new account and backing out) so the still-active profile's cookies are
     * loaded back into [YouTube.cookie] instead of leaving the app signed out.
     */
    fun restoreActiveSession() {
        repointRuntimeSession()
    }

    /**
     * True while a switch is settling, so the UI can show progress on the
     * avatar rather than blocking the whole app behind a spinner.
     */
    val switching: StateFlow<Boolean> get() = sharedSwitching.asStateFlow()

    /**
     * Move the app onto [profileId].
     *
     * Returns false when the profile is unknown or already active, so the
     * caller can skip the refresh work. Cheap and synchronous for the parts
     * that must be: the runtime session is repointed here on the calling
     * thread, and only disk caches fall to a background scope. No network
     * happens in this call, which is what lets a switch work offline and land
     * on the next frame.
     */
    fun switchTo(profileId: String): Boolean {
        val target = profileManager.get(profileId) ?: return false
        if (target.id == profileManager.activeProfileId.value) return false

        sharedSwitching.value = true
        try {
            profileManager.setActive(target.id)
            repointRuntimeSession()
            invalidateForProfileChange()
        } finally {
            sharedSwitching.value = false
        }
        return true
    }

    /**
     * Drop everything in the process that belonged to the previous profile.
     *
     * Also called after adding or removing a profile, since both can change
     * which one is active.
     */
    fun invalidateForProfileChange() {
        // Anonymous anti-bot identity: drop from memory and disk so it is
        // re-minted fresh for the profile we just switched to.
        YouTube.visitorData = null
        runCatching {
            val prefs = appContext.getSharedPreferences("flow_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .remove(VISITOR_DATA_KEY)
                .remove(VISITOR_DATA_FETCHED_AT_KEY)
                .apply()
        }
        runCatching {
            appContext.getSharedPreferences("home_feed_rotation", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
        // One account's personalised feed must not leak into another's.
        runCatching { com.omersusin.pitube.ui.screens.home.HomeFeedCache.clear() }
        backgroundScope.launch {
            runCatching {
                com.omersusin.pitube.data.local.HomeFeedCacheRepository(appContext).clearAll()
            }
        }
    }

    /** Create a device-only profile and switch to it. */
    fun addLocalProfileAndSwitch(name: String): Profile {
        val profile = profileManager.addLocalProfile(name)
        switchTo(profile.id)
        return profile
    }

    /**
     * Store a freshly captured YouTube session as a profile and switch to it.
     *
     * [datasyncId] recognises an account already in the roster, so signing back
     * into one repairs that profile rather than adding a duplicate row.
     */
    fun addYouTubeProfileAndSwitch(
        cookies: String,
        name: String? = null,
        handle: String? = null,
        email: String? = null,
        avatarUrl: String? = null,
        datasyncId: String? = null
    ): Profile {
        val profile = profileManager.addYouTubeProfile(cookies, name, handle, avatarUrl, email, datasyncId)
        if (!switchTo(profile.id)) invalidateForProfileChange()
        return profile
    }

    /**
     * The profile a quick-toggle would flip to, or null when there is nothing
     * to flip back to yet.
     */
    fun quickSwitchTarget(): Profile? =
        profileManager.previousProfileId.value
            ?.takeIf { it != profileManager.activeProfileId.value }
            ?.let { profileManager.get(it) }
            ?: profiles.value.firstOrNull { it.id != profileManager.activeProfileId.value }
                ?.takeIf { profiles.value.size == 2 }

    /**
     * Flip straight back to the last profile, skipping the sheet.
     *
     * Falls back to "the other one" when there are exactly two profiles and no
     * history yet, because with two the intent is unambiguous - and two is the
     * common case this shortcut exists for. Returns the profile switched to, or
     * null when there was nothing to switch to.
     */
    fun quickSwitch(): Profile? {
        val target = quickSwitchTarget() ?: return null
        return if (switchTo(target.id)) target else null
    }

    /**
     * Sign a YouTube profile out without removing it from the roster.
     *
     * Used when it is the only profile there is: the app must always have an
     * identity, so rather than deleting it, the account is stripped off and
     * what remains is a device-only profile.
     */
    fun signOut(profileId: String) {
        profileManager.replaceWithFreshLocal(profileId)
        if (profileManager.activeProfileId.value == profileId) {
            repointRuntimeSession()
            invalidateForProfileChange()
        }
    }

    /** Remove a profile. Returns false when it is the only one left. */
    fun remove(profileId: String): Boolean {
        val wasActive = profileManager.activeProfileId.value == profileId
        if (!profileManager.remove(profileId)) return false
        if (wasActive) {
            repointRuntimeSession()
            invalidateForProfileChange()
        }
        return true
    }

    fun rename(profileId: String, name: String) {
        profileManager.updateIdentity(profileId, name = name)
    }

    /**
     * Point the runtime session at the active profile: the consumers that read
     * [YouTube.cookie] need to see the new account's cookies immediately.
     */
    private fun repointRuntimeSession() {
        val active = profileManager.active()
        val cookies = sessionManager.getCookies()
        YouTube.cookie = cookies
        YouTube.useLoginForBrowse = !cookies.isNullOrEmpty()
        backgroundScope.launch {
            runCatching {
                val preferences = PlayerPreferences(appContext)
                if (cookies.isNullOrEmpty()) {
                    preferences.clearYoutubeAccount()
                } else {
                    preferences.setYoutubeAccount(
                        cookie = cookies,
                        name = active.name.takeIf { !active.isLocal },
                        email = active.email.takeIf { !active.isLocal },
                        thumbnailUrl = active.avatarUrl.takeIf { !active.isLocal && !it.isNullOrBlank() }
                    )
                }
            }
        }
    }

    companion object {
        // Mirror the same keys FlowApplication uses for its visitor cache.
        private const val VISITOR_DATA_KEY = "visitor_data"
        private const val VISITOR_DATA_FETCHED_AT_KEY = "visitor_data_fetched_at"

        private val sharedSwitching = MutableStateFlow(false)

        private val backgroundScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
        )
    }
}