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
            YouTube.setLikeStatus(videoId, status)
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
            YouTube.setSubscribed(channelId, subscribe)
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

    /**
     * Add / remove a video on the account's real Watch Later playlist ("WL").
     *
     * The caller already wrote the optimistic local Room entry (which is what
     * the UI toggles from, and keeps watch-later working offline), so this is
     * best-effort only: a failed network write never rolls the device state
     * back. Signed out calls no-op exactly like [setLikeStatus].
     */
    fun setVideoInWatchLater(videoId: String, add: Boolean) {
        if (!canWriteBack() || videoId.isBlank()) return
        backgroundScope.launch {
            YouTube.setVideoInWatchLater(videoId, add)
                .onFailure { Log.w("AccountActions", "setVideoInWatchLater(add=$add) failed for $videoId", it) }
                .onSuccess { ok ->
                    if (!ok) {
                        Log.w("AccountActions", "setVideoInWatchLater(add=$add) not applied for $videoId")
                    }
                }
        }
    }

    /**
     * Add / remove a video on one of the account's real playlists via
     * `browse/edit_playlist`. Best-effort, mirroring [setVideoInWatchLater].
     */
    fun setVideoInPlaylist(playlistId: String, videoId: String, add: Boolean) {
        if (!canWriteBack() || playlistId.isBlank() || videoId.isBlank()) return
        backgroundScope.launch {
            YouTube.editPlaylist(playlistId, videoId, add)
                .onFailure { Log.w("AccountActions", "setVideoInPlaylist($playlistId, add=$add) failed for $videoId", it) }
                .onSuccess { ok ->
                    if (!ok) {
                        Log.w("AccountActions", "setVideoInPlaylist($playlistId, add=$add) not applied for $videoId")
                    }
                }
        }
    }
}