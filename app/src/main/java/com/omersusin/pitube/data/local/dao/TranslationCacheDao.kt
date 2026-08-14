package com.omersusin.pitube.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.omersusin.pitube.data.local.entity.CachedTranslationEntity

@Dao
interface TranslationCacheDao {

    @Query("SELECT translatedText FROM cached_translations WHERE id = :id")
    suspend fun get(id: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CachedTranslationEntity)

    /** Keep only the most recent entries, preventing unbounded growth. */
    @Query(
        """
        DELETE FROM cached_translations
        WHERE rowid NOT IN (
            SELECT rowid FROM cached_translations ORDER BY rowid DESC LIMIT 2000
        )
        """,
    )
    suspend fun prune()
}