package com.dav3.linksentry.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [HistoryEntity::class, HandlerPrefEntity::class, DangerOverrideEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun handlerPrefDao(): HandlerPrefDao
    abstract fun dangerOverrideDao(): DangerOverrideDao
}
