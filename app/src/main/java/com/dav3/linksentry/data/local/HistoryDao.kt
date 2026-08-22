package com.dav3.linksentry.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntity): Long

    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT 200")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM history")
    suspend fun clear(): Int

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int
}
