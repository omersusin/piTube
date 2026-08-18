package com.omersusin.pitube.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.omersusin.pitube.data.local.entity.HomeFeedCacheEntity

@Dao
interface HomeFeedCacheDao {
    @Query(
        """
        SELECT * FROM home_feed_cache
        WHERE profileId = :profileId AND bucket = :bucket AND expiresAt > :now
        ORDER BY orderIndex ASC
        """,
    )
    suspend fun getFreshBucket(profileId: String, bucket: String, now: Long): List<HomeFeedCacheEntity>

    @Query(
        """
        SELECT * FROM home_feed_cache
        WHERE profileId = :profileId AND bucket = 'RELATED'
        AND relatedSeedId = :seedId AND expiresAt > :now
        ORDER BY orderIndex ASC
        """,
    )
    suspend fun getFreshRelated(profileId: String, seedId: String, now: Long): List<HomeFeedCacheEntity>

    @Query(
        """
        SELECT * FROM home_feed_cache
        WHERE profileId = :profileId AND bucket = 'RESERVE' AND expiresAt > :now
        ORDER BY cachedAt DESC, orderIndex ASC
        LIMIT :limit
        """,
    )
    suspend fun getFreshReserve(profileId: String, now: Long, limit: Int): List<HomeFeedCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HomeFeedCacheEntity>)

    @Query("DELETE FROM home_feed_cache WHERE profileId = :profileId AND bucket = :bucket")
    suspend fun clearBucket(profileId: String, bucket: String)

    @Query("DELETE FROM home_feed_cache WHERE profileId = :profileId AND bucket = 'RELATED' AND relatedSeedId = :seedId")
    suspend fun clearRelated(profileId: String, seedId: String)

    @Query("DELETE FROM home_feed_cache WHERE expiresAt <= :now")
    suspend fun deleteExpired(now: Long)

    @Query("DELETE FROM home_feed_cache WHERE videoId = :videoId")
    suspend fun deleteVideo(videoId: String)

    @Query("DELETE FROM home_feed_cache WHERE channelId = :channelId")
    suspend fun deleteChannel(channelId: String)

    @Query("DELETE FROM home_feed_cache WHERE profileId = :profileId")
    suspend fun clearProfile(profileId: String)

    @Query("DELETE FROM home_feed_cache")
    suspend fun clearAll()

    @Query(
        """
        DELETE FROM home_feed_cache
        WHERE profileId = :profileId AND bucket = 'RESERVE'
        AND cacheKey NOT IN (
            SELECT cacheKey FROM home_feed_cache
            WHERE profileId = :profileId AND bucket = 'RESERVE'
            ORDER BY cachedAt DESC, orderIndex ASC
            LIMIT :maxRows
        )
        """,
    )
    suspend fun trimReserve(profileId: String, maxRows: Int)

    @Query(
        """
        DELETE FROM home_feed_cache
        WHERE profileId = :profileId AND bucket = 'RELATED' AND relatedSeedId = :seedId
        AND cacheKey NOT IN (
            SELECT cacheKey FROM home_feed_cache
            WHERE profileId = :profileId AND bucket = 'RELATED' AND relatedSeedId = :seedId
            ORDER BY orderIndex ASC
            LIMIT :maxRows
        )
        """,
    )
    suspend fun trimRelatedSeed(profileId: String, seedId: String, maxRows: Int)

    @Query(
        """
        DELETE FROM home_feed_cache
        WHERE profileId = :profileId AND bucket = 'RELATED'
        AND relatedSeedId NOT IN (
            SELECT relatedSeedId FROM home_feed_cache
            WHERE profileId = :profileId AND bucket = 'RELATED' AND relatedSeedId IS NOT NULL
            GROUP BY relatedSeedId
            ORDER BY MAX(cachedAt) DESC
            LIMIT :maxSeeds
        )
        """,
    )
    suspend fun trimRelatedSeeds(profileId: String, maxSeeds: Int)
}
