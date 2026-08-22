package com.dav3.linksentry.domain.repository

import com.dav3.linksentry.domain.system.HandlerUsage
import kotlinx.coroutines.flow.Flow

/** Persisted "open with" preferences and usage counts per target key. */
interface HandlerPrefsRepository {
    fun observeAll(): Flow<List<HandlerUsage>>

    suspend fun forKey(key: String): List<HandlerUsage>

    /** Record a launch of [pkg] under [key]; persists or bumps count/lastUsed. */
    suspend fun recordUse(key: String, pkg: String)

    /** Remove a stored preference (e.g. app uninstalled). */
    suspend fun forget(key: String, pkg: String)
}
