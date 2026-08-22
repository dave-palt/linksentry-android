package com.dav3.linksentry.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val host: String,
    val severity: String?, // Severity name, null = no signals
    val action: String, // HistoryAction name
    val timestamp: Long,
)
