package com.omersusin.pitube.di

import android.content.Context
import com.omersusin.pitube.data.local.AppDatabase
import com.omersusin.pitube.data.local.dao.TranslationCacheDao
import com.omersusin.pitube.data.translation.TranslationEnginePrefs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TranslationModule {

    @Provides
    @Singleton
    fun provideTranslationEnginePrefs(@ApplicationContext context: Context): TranslationEnginePrefs {
        return TranslationEnginePrefs(context)
    }

    @Provides
    @Singleton
    fun provideTranslationCacheDao(database: AppDatabase): TranslationCacheDao {
        return database.translationCacheDao()
    }
}