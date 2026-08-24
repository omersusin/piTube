package com.omersusin.pitube.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.omersusin.pitube.data.local.entity.SyncLogEntity
import com.omersusin.pitube.data.local.entity.SyncPeerEntity

@Dao
interface SyncLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SyncLogEntity)

    @Query("SELECT COUNT(*) FROM sync_log WHERE peerDeviceId = :peer AND collection = :collection AND payloadHash = :hash")
    suspend fun count(peer: String, collection: String, hash: String): Int

    suspend fun isAlreadyApplied(peer: String, collection: String, hash: String): Boolean =
        count(peer, collection, hash) > 0
}

@Dao
interface SyncPeerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(peer: SyncPeerEntity)
}
