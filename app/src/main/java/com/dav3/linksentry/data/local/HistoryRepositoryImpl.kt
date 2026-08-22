package com.dav3.linksentry.data.local

import com.dav3.linksentry.domain.model.HistoryAction
import com.dav3.linksentry.domain.model.LinkRecord
import com.dav3.linksentry.domain.model.Severity
import com.dav3.linksentry.domain.repository.HistoryRepository
import com.dav3.linksentry.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val dao: HistoryDao,
    private val settingsRepository: SettingsRepository,
) : HistoryRepository {

    override fun observeAll(): Flow<List<LinkRecord>> = dao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    override suspend fun record(url: String, host: String, severity: Severity?, action: HistoryAction) {
        val settings = settingsRepository.settings.first()
        if (!settings.recordHistory) return
        dao.insert(
            HistoryEntity(
                url = url,
                host = host,
                severity = severity?.name,
                action = action.name,
                timestamp = System.currentTimeMillis(),
            ),
        )
        settings.retentionDays?.let { days ->
            dao.deleteOlderThan(System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L)
        }
    }

    override suspend fun clear() {
        dao.clear()
    }

    private fun HistoryEntity.toDomain() = LinkRecord(
        id = id,
        url = url,
        host = host,
        worstSeverity = severity?.let { runCatching { Severity.valueOf(it) }.getOrNull() },
        action = runCatching { HistoryAction.valueOf(action) }.getOrNull() ?: HistoryAction.INSPECTED,
        timestamp = timestamp,
    )
}
