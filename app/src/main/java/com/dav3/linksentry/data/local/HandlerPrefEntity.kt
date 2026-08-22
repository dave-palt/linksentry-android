package com.dav3.linksentry.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted handler preference + usage per target key.
 * Key is "domain:<host>" or "scheme:<scheme>" — see HandlerRanker.
 */
@Entity(
    tableName = "handler_prefs",
    indices = [Index(value = ["key", "packageName"], unique = true)],
)
data class HandlerPrefEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val packageName: String,
    val count: Int,
    val lastUsed: Long,
)
