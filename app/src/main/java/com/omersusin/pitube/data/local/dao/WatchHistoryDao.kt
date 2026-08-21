package com.omersusin.pitube.data.local.dao

import androidx.room.*
import com.omersusin.pitube.data.local.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    // ── Writes ──────────────────────────────────────────────────────────────

    /** Save / update a single entry (e.g. real-time playback position). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WatchHistoryEntity)

    /**
     * Bulk insert many entries at once.
     * Uses IGNORE so that actual watch-progress records already in the DB are
     * never overwritten by imported stubs.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<WatchHistoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<WatchHistoryEntity>)

    @Query("DELETE FROM watch_history WHERE videoId = :videoId AND profileId = :profileId")
    suspend fun deleteEntry(videoId: String, profileId: String)

    @Query("DELETE FROM watch_history WHERE profileId = :profileId")
    suspend fun clearAll(profileId: String)

    @Query("DELETE FROM watch_history WHERE isShort = 1 AND profileId = :profileId")
    suspend fun clearShorts(profileId: String)

    // ── Reads ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM watch_history WHERE profileId = :profileId ORDER BY timestamp DESC")
    fun getAllHistory(profileId: String): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE profileId = :profileId AND isShort = 0 AND isLocal = 0 ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLibraryHistory(profileId: String, limit: Int): Flow<List<WatchHistoryEntity>>

    /** Paged version for very large histories (UI only needs recent items). */
    @Query("SELECT * FROM watch_history WHERE profileId = :profileId ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getHistoryPage(profileId: String, limit: Int, offset: Int): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE profileId = :profileId AND isMusic = 0 AND isLocal = 0 ORDER BY timestamp DESC")
    fun getVideoHistory(profileId: String): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE profileId = :profileId AND isMusic = 1 AND isLocal = 0 ORDER BY timestamp DESC")
    fun getMusicHistory(profileId: String): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE videoId = :videoId AND profileId = :profileId")
    fun getEntry(videoId: String, profileId: String): Flow<WatchHistoryEntity?>

    @Query("SELECT position FROM watch_history WHERE videoId = :videoId AND profileId = :profileId")
    suspend fun getPosition(videoId: String, profileId: String): Long?

    @Query("SELECT COUNT(*) FROM watch_history WHERE profileId = :profileId")
    suspend fun getCountOnce(profileId: String): Int

    @Query("SELECT COUNT(*) FROM watch_history WHERE profileId = :profileId")
    fun getCount(profileId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM watch_history WHERE profileId = :profileId AND isMusic = 0 AND isLocal = 0")
    fun getVideoCount(profileId: String): Flow<Int>

    /**
     * Returns video IDs that the user has already watched (position > 0 OR appeared in history).
     * Used to filter watched shorts from the subscription shelf. Local files are excluded so the
     * recommendation/feed engine never learns from them.
     */
    @Query("SELECT videoId FROM watch_history WHERE profileId = :profileId AND isMusic = 0 AND isLocal = 0")
    suspend fun getAllWatchedVideoIds(profileId: String): List<String>

    /** All video IDs currently in history, for idempotent (re)imports. */
    @Query("SELECT videoId FROM watch_history WHERE profileId = :profileId")
    suspend fun getAllHistoryIds(profileId: String): List<String>

    @Query("""
        SELECT videoId FROM watch_history
        WHERE profileId = :profileId
        AND isMusic = 0
        AND isLocal = 0
        AND duration > 0
        AND (CAST(position AS REAL) / CAST(duration AS REAL)) * 100 >= :minPercent
        AND (duration - position) <= :maxRemainingMs
    """)
    suspend fun getWatchedVideoIdsAboveThreshold(profileId: String, minPercent: Float = 99f, maxRemainingMs: Long = Long.MAX_VALUE): List<String>

    @Query("""
        SELECT videoId FROM watch_history
        WHERE profileId = :profileId
        AND isMusic = 0
        AND isLocal = 0
        AND isShort = 1
        AND duration > 0
        AND (CAST(position AS REAL) / CAST(duration AS REAL)) * 100 >= :minPercent
        AND (duration - position) <= :maxRemainingMs
    """)
    suspend fun getWatchedShortIdsAboveThreshold(profileId: String, minPercent: Float = 99f, maxRemainingMs: Long = Long.MAX_VALUE): List<String>

    /**
     * Returns the most recently watched non-music, non-Short video **only if that specific video
     * is still in progress**.  By restricting to the maximum timestamp we avoid the
     * "stack fallback" problem where finishing one video causes the previous unfinished
     * video to pop up in the continue-watching mini-player instead.
     */
    @Query("""
        SELECT * FROM watch_history
        WHERE profileId = :profileId
        AND isMusic = 0
        AND isShort = 0
        AND isLocal = 0
        AND duration > 0
        AND position > 0
        AND (CAST(position AS REAL) / CAST(duration AS REAL)) < 0.95
        AND (duration - position) > 30000
        AND timestamp = (SELECT MAX(timestamp) FROM watch_history WHERE profileId = :profileId AND isMusic = 0 AND isShort = 0 AND isLocal = 0)
        LIMIT 1
    """)
    suspend fun getLatestUnfinishedVideo(profileId: String): WatchHistoryEntity?

    /**
     * Marks a video as fully watched by setting position = duration.
     * This excludes it from the continue-watching popup on the next launch.
     * Called when the user explicitly dismisses the restored-session mini-player.
     */
    @Query("UPDATE watch_history SET position = duration WHERE videoId = :videoId AND profileId = :profileId")
    suspend fun markAsWatched(videoId: String, profileId: String)

    // ── Legacy adoption (pre-scope rows with profileId = '') ────────────────

    @Query("UPDATE watch_history SET profileId = :targetProfileId WHERE profileId = ''")
    suspend fun adoptLegacyRows(targetProfileId: String)

    // ── Unscoped variants (device-level backup/sync only) ───────────────────

    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC")
    suspend fun getAllHistoryUnscoped(): List<WatchHistoryEntity>

    @Query("DELETE FROM watch_history WHERE videoId = :videoId")
    suspend fun deleteEntryUnscoped(videoId: String)
}
