package com.dav3.linksentry.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row per distinct URL (exact match); repeat opens bump [openCount]. */
@Entity(
    tableName = "history",
    indices = [Index(value = ["url"], unique = true)],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val host: String,
    val severity: String?, // Severity name, null = no signals
    val action: String, // HistoryAction name
    val timestamp: Long, // latest open
    val openCount: Int = 1,
    val lastAppPackage: String? = null,
    val lastAppActivity: String? = null,
    val lastAppLabel: String? = null,
)
