package com.omersusin.pitube.di

import android.content.Context
import com.omersusin.pitube.data.local.AppDatabase
import com.omersusin.pitube.data.local.dao.NotificationDao
import com.omersusin.pitube.data.local.dao.PlaylistDao
import com.omersusin.pitube.data.local.dao.VideoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideVideoDao(database: AppDatabase): VideoDao {
        return database.videoDao()
    }

    @Provides
    fun providePlaylistDao(database: AppDatabase): PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    fun provideNotificationDao(database: AppDatabase): NotificationDao {
        return database.notificationDao()
    }

    @Provides
    fun provideCacheDao(database: AppDatabase): com.omersusin.pitube.data.local.dao.CacheDao {
        return database.cacheDao()
    }

    @Provides
    fun provideDownloadDao(database: AppDatabase): com.omersusin.pitube.data.local.dao.DownloadDao {
        return database.downloadDao()
    }

    @Provides
    fun provideRecognitionHistoryDao(database: AppDatabase): com.omersusin.pitube.data.local.dao.RecognitionHistoryDao {
        return database.recognitionHistoryDao()
    }

    @Provides
    fun provideSubscriptionGroupDao(database: AppDatabase): com.omersusin.pitube.data.local.dao.SubscriptionGroupDao {
        return database.subscriptionGroupDao()
    }

    @Provides
    fun provideWatchHistoryDao(database: AppDatabase): com.omersusin.pitube.data.local.dao.WatchHistoryDao {
        return database.watchHistoryDao()
    }

    @Provides
    fun provideSyncLogDao(database: AppDatabase): com.omersusin.pitube.data.local.dao.SyncLogDao {
        return database.syncLogDao()
    }

    @Provides
    fun provideSyncPeerDao(database: AppDatabase): com.omersusin.pitube.data.local.dao.SyncPeerDao {
        return database.syncPeerDao()
    }
}
