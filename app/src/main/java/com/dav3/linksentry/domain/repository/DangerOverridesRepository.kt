package com.dav3.linksentry.domain.repository

import com.dav3.linksentry.domain.model.DangerOverride

/** User-granted "don't warn again" for dangerous links (local only). */
interface DangerOverridesRepository {
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<DangerOverride>>

    /** All overrides as a one-shot snapshot. */
    suspend fun all(): List<DangerOverride>

    /** Grant an override (idempotent). */
    suspend fun grant(override: DangerOverride)

    /** Drop every override (Settings: "warn to dangerous links again"). */
    suspend fun clearAll()

    /** Drop one override (Settings list delete / revoke from a link view). */
    suspend fun revoke(override: DangerOverride)
}
