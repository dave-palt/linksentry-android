package com.dav3.linksentry.data.local

import com.dav3.linksentry.domain.repository.HandlerPrefsRepository
import com.dav3.linksentry.domain.system.HandlerUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HandlerPrefsRepositoryImpl @Inject constructor(
    private val dao: HandlerPrefDao,
) : HandlerPrefsRepository {

    override fun observeAll(): Flow<List<HandlerUsage>> = dao.observeAll().map { rows ->
        rows.map { HandlerUsage(it.key, it.packageName, it.count, it.lastUsed) }
    }

    override suspend fun forKey(key: String): List<HandlerUsage> = dao.forKey(key).map { HandlerUsage(it.key, it.packageName, it.count, it.lastUsed) }

    override suspend fun recordUse(key: String, pkg: String) {
        val existing = dao.forKey(key).firstOrNull { it.packageName == pkg }
        val now = System.currentTimeMillis()
        dao.upsert(
            HandlerPrefEntity(
                id = existing?.id ?: 0,
                key = key,
                packageName = pkg,
                count = (existing?.count ?: 0) + 1,
                lastUsed = now,
            ),
        )
    }

    override suspend fun forget(key: String, pkg: String) {
        dao.delete(key, pkg)
    }

    override suspend fun clearAll() = dao.clearAll()

    override suspend fun clearKey(key: String) = dao.clearKey(key)
}
