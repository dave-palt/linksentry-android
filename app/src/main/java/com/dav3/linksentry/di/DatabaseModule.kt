package com.dav3.linksentry.di

import android.content.Context
import androidx.room.Room
import com.dav3.linksentry.data.local.HandlerPrefDao
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
        .addMigrations(MIGRATION_1_2)
        .build()

    @Provides
    @Singleton
    fun provideHandlerPrefDao(db: HistoryDatabase): HandlerPrefDao = db.handlerPrefDao()

    private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `handler_prefs` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`key` TEXT NOT NULL, " +
                    "`packageName` TEXT NOT NULL, " +
                    "`count` INTEGER NOT NULL, " +
                    "`lastUsed` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_handler_prefs_key_packageName` ON `handler_prefs` (`key`, `packageName`)")
        }
    }

    @Provides
    @Singleton
    fun provideDao(db: HistoryDatabase): HistoryDao = db.historyDao()
}
