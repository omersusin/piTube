package com.omersusin.pitube.data.local

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single shared entry point for materializing the signed-in account's real
 * FEhistory rows into the local resume store. HistoryViewModel and
 * TimeManagementViewModel both used to import independently — two uncoordinated
 * full account-history fetches per session (and the per-instance one-shot guard
 * re-fetched on every screen reopen). This consolidates them: at most one fetch
 * per [STALE_MS] per profile, idempotent fresh-IDs-only writes so existing rows
 * keep their real timestamps and resume positions.
 */
@Singleton
class HistoryAccountSync
    @Inject
    constructor(
        private val viewHistory: ViewHistory,
        private val youTubeRepository: com.omersusin.pitube.data.repository.YouTubeRepository,
    ) {
        private val inFlight = AtomicBoolean(false)
        private val lastImportAtMs = AtomicLong(0L)

        /**
         * Pulls account history and inserts only IDs the local store has never
         * seen. Returns the number of freshly materialized entries.
         *
         * @param force bypass the staleness window (explicit user action).
         */
        suspend fun importIfStale(
            force: Boolean = false,
            staleMs: Long = STALE_MS,
        ): Int {
            if (!youTubeRepository.isSignedIn) {
                Log.w(TAG, "HistorySync skipped: not-signed-in")
                return 0
            }
            val now = System.currentTimeMillis()
            if (!force && now - lastImportAtMs.get() < staleMs) return 0
            if (!inFlight.compareAndSet(false, true)) return 0
            try {
                var inserted = 0
                val videos = youTubeRepository.getYouTubeHistory()
                val existingIds = viewHistory.getAllHistoryIds()
                videos.forEach { video ->
                    if (video.id in existingIds) return@forEach
                    viewHistory.touchHistoryEntry(
                        videoId = video.id,
                        title = video.title,
                        thumbnailUrl = video.thumbnailUrl,
                        channelName = video.channelName,
                        channelId = video.channelId,
                        duration = video.duration * 1000L,
                    )
                    inserted++
                }
                // Stamp the window only on a non-empty fetch so a transient
                // empty shell (parse drift, network) retries on next open
                // instead of being suppressed for the whole staleness period.
                if (videos.isNotEmpty()) lastImportAtMs.set(System.currentTimeMillis())
                Log.i(
                    TAG,
                    "HistorySync done: fetched=${videos.size} existing=${existingIds.size} new=$inserted",
                )
                return inserted
            } catch (e: Exception) {
                Log.w(TAG, "Account history sync failed", e)
                return 0
            } finally {
                inFlight.set(false)
            }
        }

        companion object {
            private const val TAG = "HistoryAccountSync"

            /** One account-history pull per process per 6 hours, max. */
            private const val STALE_MS = 6L * 60 * 60 * 1000
        }
    }
