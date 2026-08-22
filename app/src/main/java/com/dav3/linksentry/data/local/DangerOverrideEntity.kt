package com.dav3.linksentry.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A user-granted "don't warn me again about this" for dangerous links. */
@Entity(tableName = "danger_overrides")
data class DangerOverrideEntity(
    @PrimaryKey val key: String,
    val createdAt: Long,
)
