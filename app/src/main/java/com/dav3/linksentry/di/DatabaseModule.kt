package com.dav3.linksentry.di

import android.content.Context
import androidx.room.Room
import com.dav3.linksentry.data.local.DangerOverrideDao
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
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        .build()

    @Provides
    @Singleton
    fun provideHandlerPrefDao(db: HistoryDatabase): HandlerPrefDao = db.handlerPrefDao()

    @Provides
    @Singleton
    fun provideDangerOverrideDao(db: HistoryDatabase): DangerOverrideDao = db.dangerOverrideDao()

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

    private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `danger_overrides` (" +
                    "`key` TEXT PRIMARY KEY NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)",
            )
        }
    }

    /** v3 -> v4: one row per distinct URL + last-opened-app tracking. Old
     *  duplicate rows are collapsed by URL (max count kept, newest wins). */
    private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `history_v4` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`url` TEXT NOT NULL, `host` TEXT NOT NULL, " +
                    "`severity` TEXT, `action` TEXT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, `openCount` INTEGER NOT NULL DEFAULT 1, " +
                    "`lastAppPackage` TEXT, `lastAppActivity` TEXT, `lastAppLabel` TEXT)",
            )
            db.execSQL(
                "INSERT INTO history_v4 (url, host, severity, action, timestamp, openCount) " +
                    "SELECT url, host, severity, action, MAX(timestamp), COUNT(*) " +
                    "FROM history GROUP BY url",
            )
            db.execSQL("DROP TABLE IF EXISTS `history`")
            db.execSQL("ALTER TABLE `history_v4` RENAME TO `history`")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_history_url` ON `history` (`url`)")
        }
    }

    /** v4 -> v5: open counts were inflated by re-inspections (each inspect
     *  bumped the counter). Normalize: only genuinely-opened rows keep 1. */
    private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                "UPDATE history SET openCount = CASE WHEN action = 'OPENED_WITH' THEN 1 ELSE 0 END",
            )
        }
    }

    @Provides
    @Singleton
    fun provideDao(db: HistoryDatabase): HistoryDao = db.historyDao()
}
