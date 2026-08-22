package com.dav3.linksentry.data.local

import com.dav3.linksentry.domain.model.DangerOverride
import com.dav3.linksentry.domain.repository.DangerOverridesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DangerOverridesRepositoryImpl @Inject constructor(
    private val dao: DangerOverrideDao,
) : DangerOverridesRepository {
    override fun observeAll(): Flow<List<DangerOverride>> = dao.observeAll().map { rows ->
        rows.mapNotNull { row -> parseKey(row.key) }
    }

    override suspend fun all(): List<DangerOverride> = dao.observeAll().first().mapNotNull { row -> parseKey(row.key) }

    override suspend fun grant(override: DangerOverride) {
        dao.upsert(DangerOverrideEntity(key = override.key(), createdAt = System.currentTimeMillis()))
    }

    override suspend fun clearAll() = dao.clearAll()

    override suspend fun revoke(override: DangerOverride) = dao.delete(override.key())

    private fun parseKey(key: String): DangerOverride? {
        val hostPart = key.removePrefix("danger:host:")
        if (hostPart != key) return DangerOverride.Host(hostPart)
        val signalsPart = key.removePrefix("danger:signals:")
        if (signalsPart != key) {
            val names = signalsPart.split(",").filter { it.isNotBlank() }
            if (names.isEmpty()) return null
            val ids = names.mapNotNull { runCatching { com.dav3.linksentry.domain.model.SignalId.valueOf(it) }.getOrNull() }
            if (ids.size != names.size) return null
            return DangerOverride.Signals(ids.toSet())
        }
        return null
    }
}
