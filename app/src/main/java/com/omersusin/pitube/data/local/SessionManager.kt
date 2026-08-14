package com.omersusin.pitube.data.local

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The active profile's YouTube session.
 *
 * Adapted from Koda's SessionManager. Every consumer in this app resolves the
 * session fresh on each call - the runtime cookie lives on the InnerTube
 * singleton, which is pointed at the active profile - so redirecting what "the
 * session" means is all [ProfileManager.setActive] does, and that moves the
 * whole app onto another account without touching any of them. That is why
 * switching accounts needs no re-authentication and works offline.
 *
 * The plain [PlayerPreferences] DataStore keys are kept as a **mirror** of the
 * active profile's session ([PlayerPreferences.youtubeCookie],
 * [PlayerPreferences.youtubeAccountName], [PlayerPreferences.youtubeAccountThumbnail]).
 * Every write here writes through to both the encrypted per-profile store and
 * the mirror, so the existing UI that reads the DataStore keys keeps working
 * unchanged while the roster lives encrypted in [ProfileManager].
 */
class SessionManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val profileManager = ProfileManager(appContext)
    private val playerPreferences = PlayerPreferences(appContext)

    fun saveUserAvatar(url: String) {
        profileManager.updateIdentity(activeId(), avatarUrl = url)
        runCatching { runMirror { it.updateYoutubeAccountInfo(name = null, email = null, thumbnailUrl = url) } }
    }

    fun saveUserEmail(email: String?) {
        profileManager.updateIdentity(activeId(), email = email)
    }

    fun getUserAvatar(): String? = profileManager.active().avatarUrl

    fun getUserEmail(): String? = profileManager.active().email

    /**
     * Save session cookies obtained from WebView.
     *
     * Also the refresh path for cookie rotation, so it does not clear the
     * sign-in verdict - only a deliberate log-in does that. Use
     * [startSession] when the user has just logged in.
     *
     * A rotation arriving while a local profile is active is dropped: there is
     * no account to refresh, and writing it would quietly turn a device-only
     * profile into a signed-in one.
     */
    fun saveCookies(cookies: String) {
        val active = profileManager.active()
        if (active.isLocal) return
        profileManager.saveCookiesFor(active.id, cookies)
        runCatching { runMirror { it.refreshYoutubeCookie(cookies) } }
    }

    /**
     * Record that YouTube answered an authenticated request as anonymous, or
     * that it accepted one. Cookies are left alone either way - they are the
     * only thing a later refresh has to work with, and clearing them on a
     * single bad response would sign people out over a hiccup.
     *
     * The verdict is stored against the profile it came from, so with several
     * accounts in the roster the badge lands on the right row.
     */
    fun setSessionExpired(expired: Boolean) {
        val active = profileManager.active()
        if (active.isLocal) return
        profileManager.setExpired(active.id, expired)
        if (_sessionExpired.value != expired) {
            _sessionExpired.value = expired
        }
    }

    /**
     * Begin a session the user has just signed into.
     *
     * When a local profile is active this promotes the sign-in into a new
     * YouTube profile and switches to it, so the existing login flow keeps
     * working unchanged and simply produces a profile as a side effect.
     */
    fun startSession(cookies: String) {
        val active = profileManager.active()
        if (active.isLocal) {
            val profile = profileManager.addYouTubeProfile(cookies)
            profileManager.setActive(profile.id)
        } else {
            profileManager.saveCookiesFor(active.id, cookies)
        }
        runCatching { runMirror { it.refreshYoutubeCookie(cookies) } }
        setSessionExpired(false)
    }

    /** Get the active profile's stored session cookies. */
    fun getCookies(): String? {
        val active = profileManager.active()
        if (active.isLocal) return null
        return profileManager.cookiesFor(active.id)
    }

    /**
     * Sign the active profile out.
     *
     * With a roster this means removing that profile and falling back to
     * another, rather than wiping the store: signing out of one account must
     * not take the others with it. When it is the only profile left, it is
     * emptied into a device-only profile instead, so the app always has an
     * identity to run as.
     */
    fun clearSession() {
        val active = profileManager.active()
        if (!profileManager.remove(active.id)) {
            profileManager.replaceWithFreshLocal(active.id)
        }
        _sessionExpired.value = false
        runCatching { runMirror { it.clearYoutubeAccount() } }
    }

    /**
     * Check if user is logged in.
     *
     * Deliberately still just "cookies exist", so requests keep going out and
     * a rotation can revive a session that looked dead.
     */
    fun isLoggedIn(): Boolean = !getCookies().isNullOrBlank()

    fun saveUserName(name: String) {
        profileManager.updateIdentity(activeId(), name = name)
        runCatching { runMirror { it.updateYoutubeAccountInfo(name = name, email = null, thumbnailUrl = null) } }
    }

    fun getUserName(): String? {
        val active = profileManager.active()
        return active.name.takeIf { !active.isLocal }
    }

    private fun activeId(): String = profileManager.active().id

    /**
     * Re-read the active profile's expired verdict, after a switch.
     */
    fun refreshExpiredFromProfile() {
        _sessionExpired.value = profileManager.active().expired
    }

    /**
     * True once YouTube has answered an authenticated call as anonymous.
     *
     * Companion-scoped on purpose: with no DI every ViewModel news up its own
     * SessionManager, so an instance flow would never reach the screens that
     * need to react. It mirrors the *active* profile's flag; the durable
     * per-profile value lives on [Profile.expired].
     */
    companion object {
        private val _sessionExpired = MutableStateFlow(false)
        val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()
    }

    /**
     * Run a suspend mirror write on a background dispatcher. The mirror is
     * best-effort UX plumbing: the encrypted per-profile store is the source of
     * truth, and a pending mirror cannot be allowed to block a switch.
     */
    private fun runMirror(write: suspend (PlayerPreferences) -> Unit) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { write(playerPreferences) }
        }
    }
}