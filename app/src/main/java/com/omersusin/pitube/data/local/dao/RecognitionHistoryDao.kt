package com.omersusin.pitube.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.omersusin.pitube.data.local.entity.RecognitionHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecognitionHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: RecognitionHistoryEntity): Long

    @Query(
        "SELECT * FROM recognition_history WHERE title LIKE '%' || :query || '%' " +
            "OR artist LIKE '%' || :query || '%' ORDER BY recognizedAt DESC"
    )
    fun search(query: String): Flow<List<RecognitionHistoryEntity>>

    @Query("DELETE FROM recognition_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM recognition_history")
    suspend fun clearAll()
}
