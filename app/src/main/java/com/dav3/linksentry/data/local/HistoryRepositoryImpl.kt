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

    override fun search(query: String): Flow<List<LinkRecord>> = dao.search(likePattern(query)).map { list ->
        list.map { it.toDomain() }
    }

    override suspend fun deleteFound(query: String): Int = dao.deleteByPattern(likePattern(query))

    /** LIKE pattern with escaped %/_ and manual wildcards. */
    private fun likePattern(query: String): String {
        val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return "%$escaped%"
    }

    override suspend fun record(url: String, host: String, severity: Severity?, action: HistoryAction) {
        val settings = settingsRepository.settings.first()
        if (!settings.recordHistory) return
        val ts = System.currentTimeMillis()
        when (action) {
            HistoryAction.INSPECTED -> {
                // Refresh severity only; keep count/timestamp/last action.
                if (dao.touchInspect(url, severity?.name) == 0) {
                    dao.insert(
                        HistoryEntity(
                            url = url,
                            host = host,
                            severity = severity?.name,
                            action = action.name,
                            timestamp = ts,
                            openCount = 0,
                        ),
                    )
                }
            }
            HistoryAction.OPENED_WITH -> {
                if (dao.bumpOpened(url, ts, severity?.name) == 0) {
                    dao.insert(
                        HistoryEntity(
                            url = url,
                            host = host,
                            severity = severity?.name,
                            action = action.name,
                            timestamp = ts,
                            openCount = 1,
                        ),
                    )
                }
            }
            else -> {
                if (dao.bumpAction(url, ts, action.name, severity?.name) == 0) {
                    dao.insert(
                        HistoryEntity(
                            url = url,
                            host = host,
                            severity = severity?.name,
                            action = action.name,
                            timestamp = ts,
                            openCount = 0,
                        ),
                    )
                }
            }
        }
        settings.retentionDays?.let { days ->
            dao.deleteOlderThan(System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L)
        }
    }

    override suspend fun recordApp(url: String, pkg: String, activity: String?, label: String?) {
        dao.updateLastApp(url, pkg, activity, label)
    }

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
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
        openCount = openCount,
        lastAppPackage = lastAppPackage,
        lastAppActivity = lastAppActivity,
        lastAppLabel = lastAppLabel,
    )
}
