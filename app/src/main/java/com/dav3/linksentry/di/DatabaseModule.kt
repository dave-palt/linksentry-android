package com.dav3.linksentry.di

import android.content.Context
import androidx.room.Room
import com.dav3.linksentry.data.local.HistoryDao
import com.dav3.linksentry.data.local.HistoryDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): HistoryDatabase = Room.databaseBuilder(context, HistoryDatabase::class.java, "linksentry.db")
        .build()

    @Provides
    @Singleton
    fun provideDao(db: HistoryDatabase): HistoryDao = db.historyDao()
}
