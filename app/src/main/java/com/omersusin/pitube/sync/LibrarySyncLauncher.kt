package com.omersusin.pitube.sync

import android.content.Context
import android.util.Log
import com.omersusin.pitube.data.local.YouTubeLibrarySync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object LibrarySyncLauncher {
    private const val TAG = "LibrarySyncLauncher"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val mutex = Mutex()
    private var isRunning: Boolean = false
    private val pending = mutableListOf<(com.omersusin.pitube.data.local.LibrarySyncResult) -> Unit>()

    fun refreshPinnedListsInBackground(context: Context) {
        scope.launch { runCatching { YouTubeLibrarySync.refreshPinnedLists(context.applicationContext) } }
    }

    fun syncInBackground(
        context: Context,
        onDone: ((com.omersusin.pitube.data.local.LibrarySyncResult) -> Unit)? = null,
    ) {
        val appContext = context.applicationContext
        scope.launch {
            val shouldStart: Boolean
            mutex.withLock {
                if (isRunning) {
                    if (onDone != null) pending.add(onDone)
                    shouldStart = false
                } else {
                    isRunning = true
                    shouldStart = true
                }
            }
            if (!shouldStart) {
                Log.d(TAG, "Sync already running — coalescing request")
                return@launch
            }
            try {
                val result = runCatching { YouTubeLibrarySync.sync(appContext) }
                    .getOrElse { r ->
                        Log.w(TAG, "Library sync failed", r)
                        com.omersusin.pitube.data.local.LibrarySyncResult(error = r.message)
                    }
                val toNotify: List<(com.omersusin.pitube.data.local.LibrarySyncResult) -> Unit>
                mutex.withLock {
                    toNotify = pending.toList()
                    pending.clear()
                }
                withContext(Dispatchers.Main) {
                    onDone?.invoke(result)
                    toNotify.forEach { it(result) }
                }
            } finally {
                mutex.withLock { isRunning = false }
            }
        }
    }
}
