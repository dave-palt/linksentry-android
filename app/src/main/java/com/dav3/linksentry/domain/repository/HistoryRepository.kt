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

    /** Delete one history row (swipe/trash in the History list). */
    suspend fun delete(id: Long)

    /** Delete every row for one exact URL (per-link history opt-out);
     *  returns rows removed. */
    suspend fun deleteByUrl(url: String): Int

    /** Remember which app last handled [url] (History direct re-open). */
    suspend fun recordApp(url: String, pkg: String, activity: String?, label: String?)

    /** History rows whose URL or host contains [query] (case-insensitive). */
    fun search(query: String): Flow<List<LinkRecord>>

    /** Delete every row matching [query]; returns rows removed. */
    suspend fun deleteFound(query: String): Int
}
