package com.omersusin.pitube.di

import android.content.Context
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.data.repository.YouTubeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideYouTubeRepository(playerPreferences: PlayerPreferences): YouTubeRepository {
        return YouTubeRepository.getInstance(playerPreferences)
    }

    @Provides
    @Singleton
    fun provideSubscriptionRepository(@ApplicationContext context: Context): com.omersusin.pitube.data.local.SubscriptionRepository {
        return com.omersusin.pitube.data.local.SubscriptionRepository.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideLikedVideosRepository(@ApplicationContext context: Context): com.omersusin.pitube.data.local.LikedVideosRepository {
        return com.omersusin.pitube.data.local.LikedVideosRepository.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideViewHistory(@ApplicationContext context: Context): com.omersusin.pitube.data.local.ViewHistory {
        return com.omersusin.pitube.data.local.ViewHistory.getInstance(context)
    }


    // VideoDownloadManager is now @Singleton @Inject — Hilt provides it automatically
    @Provides
    @Singleton
    fun providePlayerPreferences(@ApplicationContext context: Context): com.omersusin.pitube.data.local.PlayerPreferences {
        return com.omersusin.pitube.data.local.PlayerPreferences(context)
    }

    @Provides
    @Singleton
    fun provideShortsRepository(@ApplicationContext context: Context): com.omersusin.pitube.data.shorts.ShortsRepository {
        return com.omersusin.pitube.data.shorts.ShortsRepository.getInstance(context)
    }
}
