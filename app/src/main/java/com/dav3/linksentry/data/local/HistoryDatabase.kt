package com.dav3.linksentry.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [HistoryEntity::class, HandlerPrefEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun handlerPrefDao(): HandlerPrefDao
}
