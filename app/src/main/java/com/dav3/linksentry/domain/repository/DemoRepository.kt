package com.dav3.linksentry.domain.repository

import kotlinx.coroutines.flow.Flow

/** First-open demo flags, one per tab. */
enum class DemoKey { TOUR, HISTORY, SETTINGS }

interface DemoRepository {
    /** Emits the current seen-flag whenever it changes. */
    fun observe(key: DemoKey): Flow<Boolean>

    suspend fun isSeen(key: DemoKey): Boolean

    suspend fun markSeen(key: DemoKey)

    /** Clears the flag (Settings "replay intro tour"). */
    suspend fun unmarkSeen(key: DemoKey)
}
