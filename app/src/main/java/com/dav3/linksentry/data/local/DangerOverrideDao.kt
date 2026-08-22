package com.dav3.linksentry.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DangerOverrideDao {
    @Query("SELECT * FROM danger_overrides")
    fun observeAll(): Flow<List<DangerOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DangerOverrideEntity)

    @Query("DELETE FROM danger_overrides")
    suspend fun clearAll()

    @Query("DELETE FROM danger_overrides WHERE `key` = :key")
    suspend fun delete(key: String)
}
