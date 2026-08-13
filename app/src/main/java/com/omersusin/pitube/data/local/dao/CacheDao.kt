package com.omersusin.pitube.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.omersusin.pitube.data.local.entity.MusicHomeCacheEntity
import com.omersusin.pitube.data.local.entity.MusicHomeChipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheDao {
    // Music
    @Query("SELECT * FROM music_home_cache ORDER BY orderBy ASC")
    fun getMusicHomeSections(): Flow<List<MusicHomeCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMusicHomeSections(sections: List<MusicHomeCacheEntity>)

    @Query("DELETE FROM music_home_cache")
    suspend fun clearMusicHomeCache()

    // Music Chips
    @Query("SELECT * FROM music_home_chips_cache ORDER BY orderBy ASC")
    fun getMusicHomeChips(): Flow<List<MusicHomeChipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMusicHomeChips(chips: List<MusicHomeChipEntity>)

    @Query("DELETE FROM music_home_chips_cache")
    suspend fun clearMusicHomeChips()
}
