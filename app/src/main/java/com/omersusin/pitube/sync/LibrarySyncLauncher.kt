package com.omersusin.pitube.sync

import android.content.Context
import android.util.Log
import com.omersusin.pitube.data.local.YouTubeLibrarySync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App-scope launcher for the YouTube library sync.
 *
 * Sync must NEVER run on a composition scope (rememberCoroutineScope /
 * LaunchedEffect): navigating away mid-sync forgets that scope and cancels the
 * crawl mid-flight, surfacing as "Playlist sync failed …
 * ForgottenCoroutineScopeException" and leaving the fresh account half-synced.
 * UI layers call [syncAndNotify] and keep only their spinner state locally;
 * results are delivered through the persisted sync counters.
 */
object LibrarySyncLauncher {
    private const val TAG = "LibrarySyncLauncher"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var isRunning: Boolean = false
        private set

    /**
     * Fire-and-forget full library sync (liked videos, playlists, subscriptions).
     * Safe to call repeatedly; concurrent calls are coalesced.
     */
    fun syncInBackground(
        context: Context,
        onDone: ((com.omersusin.pitube.data.local.LibrarySyncResult) -> Unit)? = null,
    ) {
        if (isRunning) {
            Log.d(TAG, "Sync already running — coalescing request")
            return
        }
        isRunning = true
        val appContext = context.applicationContext
        scope.launch {
            val result = runCatching { YouTubeLibrarySync.sync(appContext) }
                .getOrElse { r ->
                    Log.w(TAG, "Library sync failed", r)
                    com.omersusin.pitube.data.local.LibrarySyncResult(error = r.message)
                }
            isRunning = false
            withContext(Dispatchers.Main) { onDone?.invoke(result) }
        }
    }
}
