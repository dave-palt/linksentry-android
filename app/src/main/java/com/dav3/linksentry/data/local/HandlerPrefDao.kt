package com.dav3.linksentry.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HandlerPrefDao {
    @Query("SELECT * FROM handler_prefs")
    fun observeAll(): Flow<List<HandlerPrefEntity>>

    @Query("SELECT * FROM handler_prefs WHERE `key` = :key")
    suspend fun forKey(key: String): List<HandlerPrefEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HandlerPrefEntity)

    @Query("DELETE FROM handler_prefs WHERE `key` = :key AND packageName = :pkg")
    suspend fun delete(key: String, pkg: String)

    @Query("DELETE FROM handler_prefs")
    suspend fun clearAll()

    @Query("DELETE FROM handler_prefs WHERE `key` = :key")
    suspend fun clearKey(key: String)
}
