package com.dav3.linksentry.domain.repository

import com.dav3.linksentry.domain.model.HistoryAction
import com.dav3.linksentry.domain.model.LinkRecord
import com.dav3.linksentry.domain.model.Severity
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun observeAll(): Flow<List<LinkRecord>>

    /** Records an inspection; prunes entries older than the retention window. */
    suspend fun record(url: String, host: String, severity: Severity?, action: HistoryAction)

    suspend fun clear()
}
