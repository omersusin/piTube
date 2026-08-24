package com.omersusin.pitube.data.local

import androidx.room.Database
import androidx.room.Room.databaseBuilder
import androidx.room.RoomDatabase
import com.omersusin.pitube.data.local.dao.CacheDao
import com.omersusin.pitube.data.local.dao.DownloadDao
import com.omersusin.pitube.data.local.dao.HomeFeedCacheDao
import com.omersusin.pitube.data.local.dao.NotificationDao
import com.omersusin.pitube.data.local.dao.PlaylistDao
import com.omersusin.pitube.data.local.dao.RecognitionHistoryDao
import com.omersusin.pitube.data.local.dao.SubscriptionGroupDao
import com.omersusin.pitube.data.local.dao.SyncLogDao
import com.omersusin.pitube.data.local.dao.SyncPeerDao
import com.omersusin.pitube.data.local.dao.TranslationCacheDao
import com.omersusin.pitube.data.local.dao.VideoDao
import com.omersusin.pitube.data.local.dao.WatchHistoryDao
import com.omersusin.pitube.data.local.entity.CachedTranslationEntity
import com.omersusin.pitube.data.local.entity.DownloadEntity
import com.omersusin.pitube.data.local.entity.DownloadItemEntity
import com.omersusin.pitube.data.local.entity.HomeFeedCacheEntity
import com.omersusin.pitube.data.local.entity.MusicHomeCacheEntity
import com.omersusin.pitube.data.local.entity.MusicHomeChipEntity
import com.omersusin.pitube.data.local.entity.NotificationEntity
import com.omersusin.pitube.data.local.entity.PlaylistEntity
import com.omersusin.pitube.data.local.entity.PlaylistVideoCrossRef
import com.omersusin.pitube.data.local.entity.RecognitionHistoryEntity
import com.omersusin.pitube.data.local.entity.SubscriptionGroupEntity
import com.omersusin.pitube.data.local.entity.SyncLogEntity
import com.omersusin.pitube.data.local.entity.SyncPeerEntity
import com.omersusin.pitube.data.local.entity.VideoEntity
import com.omersusin.pitube.data.local.entity.WatchHistoryEntity
import com.omersusin.pitube.data.local.migrations.MIGRATIONS

@Database(
    entities = [
        VideoEntity::class,
        PlaylistEntity::class,
        PlaylistVideoCrossRef::class,
        NotificationEntity::class,
        MusicHomeCacheEntity::class,
        MusicHomeChipEntity::class,
        DownloadEntity::class,
        DownloadItemEntity::class,
        WatchHistoryEntity::class,
        HomeFeedCacheEntity::class,
        SubscriptionGroupEntity::class,
        RecognitionHistoryEntity::class,
        SyncLogEntity::class,
        SyncPeerEntity::class,
        CachedTranslationEntity::class,
    ],
    version = 29,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun notificationDao(): NotificationDao

    abstract fun cacheDao(): CacheDao


    abstract fun downloadDao(): DownloadDao

    abstract fun watchHistoryDao(): WatchHistoryDao

    abstract fun homeFeedCacheDao(): HomeFeedCacheDao

    abstract fun subscriptionGroupDao(): SubscriptionGroupDao

    abstract fun recognitionHistoryDao(): RecognitionHistoryDao

    abstract fun syncLogDao(): SyncLogDao

    abstract fun syncPeerDao(): SyncPeerDao

    abstract fun translationCacheDao(): TranslationCacheDao

    companion object {
        @Volatile
        @Suppress("ktlint:standard:property-naming")
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                val instance =
                    databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "flow_database",
                    ).addMigrations(*MIGRATIONS)
                        .fallbackToDestructiveMigration(false)
                        .build()
                INSTANCE = instance
                instance
            }
    }
}
