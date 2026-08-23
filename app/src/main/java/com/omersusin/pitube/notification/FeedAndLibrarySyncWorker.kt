package com.omersusin.pitube.notification

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.omersusin.pitube.data.local.HomeFeedCacheRepository
import com.omersusin.pitube.data.local.NotificationSync
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.data.local.YouTubeLibrarySync
import com.omersusin.pitube.innertube.YouTube
import com.omersusin.pitube.ui.screens.home.HomeFeedCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Keeps the home feed and the account library fresh while the user is not
 * opening the app: re-rotates the anonymous visitor identity (so the feed
 * doesn't keep serving the same pinned items), invalidates the cached feed,
 * and re-pulls the account library (liked videos / playlists / subscriptions)
 * when the app is signed in.
 */
class FeedAndLibrarySyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    companion object {
        const val WORK_NAME = "feed_and_library_sync_work"
        private const val TAG = "FeedAndLibrarySyncWorker"
        private const val PERIOD_HOURS = 12L

        suspend fun schedulePeriodicSync(
            context: Context,
            reschedule: Boolean = false,
        ) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val workRequest =
                PeriodicWorkRequestBuilder<FeedAndLibrarySyncWorker>(
                    PERIOD_HOURS,
                    TimeUnit.HOURS,
                ).setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        WorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS,
                    ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                periodicWorkPolicy(reschedule),
                workRequest,
            )
            Log.d(TAG, "Scheduled feed & library sync every $PERIOD_HOURS hours")
        }

        fun cancelScheduledSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled scheduled feed & library sync")
        }
    }

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val preferences = PlayerPreferences(applicationContext)

            // 1. Rotate the ANONYMOUS visitor identity so home/trending stop
            //    returning the same pinned items between visits. Signed-in
            //    sessions must never rotate: pairing a fresh visitor id with
            //    account cookies is what empties FEwhat_to_watch.
            val loggedInEarly =
                com.omersusin.pitube.data.local.SessionManager(applicationContext)
                    .getCookies()?.isNotBlank() == true
            if (!loggedInEarly) {
                runCatching { YouTube.rotateVisitorData() }
                    .onSuccess { Log.d(TAG, "Feed visitor data rotated") }
                    .onFailure { Log.w(TAG, "Visitor rotation failed: ${it.message}") }
            }

            // 2. Drop the in-memory feed cache + persisted Room feed so the next
            //    Home screen visit fetches a genuinely fresh mix, and bump the
            //    discovery rotation epoch so even the non-personalized lane
            //    reshuffles instead of replaying the same canned searches.
            HomeFeedCache.clear()
            runCatching {
                HomeFeedCacheRepository(applicationContext).clearAll()
            }.onFailure { Log.w(TAG, "Feed cache clear failed: ${it.message}") }
            runCatching {
                applicationContext
                    .getSharedPreferences("home_feed_rotation", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putLong("discovery_epoch_time", 0L)
                    .apply()
            }.onFailure { Log.w(TAG, "Discovery rotation reset failed: ${it.message}") }

            // 3. Re-pull the account library when signed in and the last sync is
            //    older than the auto interval (matches the 12h worker cadence —
            //    the user wants continuously-fresh account data, not snapshots).
            val loggedIn = loggedInEarly
            if (loggedIn) {
                val syncedAt = runCatching { preferences.youtubeLibrarySyncedAt.first() }.getOrNull() ?: 0L
                val autoIntervalMs = TimeUnit.HOURS.toMillis(12)
                if (System.currentTimeMillis() - syncedAt > autoIntervalMs) {
                    runCatching { YouTubeLibrarySync.sync(applicationContext) }
                        .onSuccess { result ->
                            Log.d(
                                TAG,
                                "Auto library sync: liked=${result.likedVideos}, " +
                                    "playlists=${result.playlists}, channels=${result.subscribedChannels}"
                            )
                        }
                        .onFailure { Log.w(TAG, "Auto library sync failed: ${it.message}") }
                } else {
                    Log.d(TAG, "Library sync still fresh, skipping")
                }
            } else {
                Log.d(TAG, "Not signed in, skipping library sync")
            }

            // 4. Mirror the account's notification inbox so the in-app
            //    Notifications screen and unread badge stay fresh.
            if (loggedIn) {
                runCatching { NotificationSync.sync(applicationContext) }
                    .onSuccess {
                        Log.d(TAG, "Notification inbox synced")
                    }
                    .onFailure { Log.w(TAG, "Notification inbox sync failed: ${it.message}") }
            }

            Result.success()
        }
}