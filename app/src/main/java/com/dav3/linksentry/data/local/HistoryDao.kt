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

    /** Re-inspection: refresh severity only — never clobbers open state. */
    @Query("UPDATE history SET severity = :severity WHERE url = :url")
    suspend fun touchInspect(url: String, severity: String?): Int

    /** Real app launch: count + timestamp + action + severity. 0 = absent. */
    @Query(
        "UPDATE history SET openCount = openCount + 1, timestamp = :ts, " +
            "action = 'OPENED_WITH', severity = :severity WHERE url = :url",
    )
    suspend fun bumpOpened(url: String, ts: Long, severity: String?): Int

    /** Copy/share: timestamp + action + severity, no open count. 0 = absent. */
    @Query(
        "UPDATE history SET timestamp = :ts, action = :action, severity = :severity " +
            "WHERE url = :url",
    )
    suspend fun bumpAction(url: String, ts: Long, action: String, severity: String?): Int

    /** Track the most recent handler app for direct re-open from history. */
    @Query(
        "UPDATE history SET lastAppPackage = :pkg, lastAppActivity = :activity, " +
            "lastAppLabel = :label WHERE url = :url",
    )
    suspend fun updateLastApp(url: String, pkg: String, activity: String?, label: String?): Int

    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT 200")
    fun observeAll(): Flow<List<HistoryEntity>>

    /** Filter by URL/host substring (pattern includes % wildcards + ESCAPE). */
    @Query(
        "SELECT * FROM history WHERE url LIKE :pattern ESCAPE '\\' " +
            "OR host LIKE :pattern ESCAPE '\\' ORDER BY timestamp DESC LIMIT 200",
    )
    fun search(pattern: String): Flow<List<HistoryEntity>>

    /** Delete every row matching the filter; returns rows removed. */
    @Query(
        "DELETE FROM history WHERE url LIKE :pattern ESCAPE '\\' " +
            "OR host LIKE :pattern ESCAPE '\\'",
    )
    suspend fun deleteByPattern(pattern: String): Int

    @Query("DELETE FROM history WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM history")
    suspend fun clear(): Int

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Delete an exact URL row (per-link history opt-out). */
    @Query("DELETE FROM history WHERE url = :url")
    suspend fun deleteByUrl(url: String): Int

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int
}
