package com.omersusin.pitube.data.local

import android.content.Context
import android.util.Log
import com.omersusin.pitube.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The account-linked half of a like / subscribe tap.
 *
 * Device-local actions (liking, subscribing) must ALSO take effect on the
 * real Google account so the app matches official YouTube. This is the one
 * place that knows how to write back: [YouTube.setLikeStatus] and
 * [YouTube.setSubscribed] are both signed InnerTube calls that fail silently
 * when signed out, and both callers already wrote the optimistic local state,
 * so a failed network write never rolls a working device state back.
 *
 * On a successful subscribe the subscriptions feed is refreshed in the
 * background so the new channel's uploads appear immediately (dynamic sync);
 * the like half is best-effort only, matching how YouTube treats the "LL"
 * playlist.
 */
class AccountActions(context: Context) {

    private val appContext = context.applicationContext
    private val sessionManager = SessionManager(appContext)

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** True when the active profile is signed into a Google account. */
    fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    /** True when a signed account is active (so a subscribe can land remotely). */
    fun canWriteBack(): Boolean = isLoggedIn() && !YouTube.cookie.isNullOrBlank()

    /**
     * Like / unlike a video on the account. [status] is `"LIKE"`, `"DISLIKE"`
     * or null (clears the rating). Best-effort: never throws.
     */
    fun setLikeStatus(videoId: String, status: String?) {
        if (!canWriteBack() || videoId.isBlank()) return
        backgroundScope.launch {
            runCatching { YouTube.setLikeStatus(videoId, status) }
                .onFailure { Log.w("AccountActions", "setLikeStatus failed for $videoId", it) }
                .onSuccess { ok ->
                    if (ok) {
                        // Keep the local "Liked videos" library matching the account.
                        backgroundScope.launch {
                            runCatching { YouTubeLibrarySync.syncLikedVideosOnly(appContext) }
                                .onFailure { Log.w("AccountActions", "post-like library refresh failed", it) }
                        }
                    }
                }
        }
    }

    /**
     * Subscribe / unsubscribe a channel on the account. On success the local
     * subscriptions feed is re-pulled so the change shows immediately.
     */
    fun setSubscribed(channelId: String, subscribe: Boolean) {
        if (!canWriteBack() || channelId.isBlank()) return
        backgroundScope.launch {
            runCatching { YouTube.setSubscribed(channelId, subscribe) }
                .onFailure { Log.w("AccountActions", "setSubscribed failed for $channelId", it) }
                .onSuccess { ok ->
                    if (ok) {
                        backgroundScope.launch {
                            runCatching { YouTubeLibrarySync.syncSubscriptionsOnly(appContext) }
                                .onFailure { Log.w("AccountActions", "post-subscribe refresh failed", it) }
                        }
                    }
                }
        }
    }
}