package com.dav3.linksentry.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dav3.linksentry.domain.model.AppSettings
import com.dav3.linksentry.domain.model.ThemeMode
import com.dav3.linksentry.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {

    private object Keys {
        val RECORD_HISTORY = booleanPreferencesKey("record_history")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val THEME = stringPreferencesKey("theme")
        val HANDLER_LAYOUT = stringPreferencesKey("handler_layout")
        val OPEN_CLEANED = booleanPreferencesKey("open_cleaned")
        val AUTO_CLOSE_ON_OPEN = booleanPreferencesKey("auto_close_on_open")
        val CUSTOM_TRACKING = stringPreferencesKey("custom_tracking_params")
        val HISTORY_EXCLUSIONS = stringPreferencesKey("history_exclusions")
    }

    private val dataStore = context.appDataStore

    override val settings: Flow<AppSettings> = dataStore.data.map { p ->
        AppSettings(
            recordHistory = p[Keys.RECORD_HISTORY] ?: true,
            // Absent key = default 30 days; explicit 0 = keep forever (null).
            retentionDays = when (val d = p[Keys.RETENTION_DAYS]) {
                null -> 30
                0 -> null
                else -> d
            },
            theme = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            openCleaned = p[Keys.OPEN_CLEANED] ?: false,
            autoCloseOnOpen = p[Keys.AUTO_CLOSE_ON_OPEN] ?: true,
            customTrackingParams = (p[Keys.CUSTOM_TRACKING] ?: "")
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
            handlerLayout = p[Keys.HANDLER_LAYOUT]?.let {
                runCatching { com.dav3.linksentry.domain.model.HandlerLayout.valueOf(it) }.getOrNull()
            } ?: com.dav3.linksentry.domain.model.HandlerLayout.LIST,
            historyExclusions = (p[Keys.HISTORY_EXCLUSIONS] ?: "")
                .split("\n").filter { it.isNotBlank() }.toSet(),
        )
    }

    override suspend fun setRecordHistory(enabled: Boolean) {
        dataStore.edit { it[Keys.RECORD_HISTORY] = enabled }
    }

    override suspend fun setRetentionDays(days: Int?) {
        dataStore.edit {
            // null (keep forever) is stored as explicit 0 to stay distinct
            // from the absent-key default of 30.
            it[Keys.RETENTION_DAYS] = days ?: 0
        }
    }

    override suspend fun setTheme(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME] = mode.name }
    }

    override suspend fun setHandlerLayout(layout: com.dav3.linksentry.domain.model.HandlerLayout) {
        dataStore.edit { it[Keys.HANDLER_LAYOUT] = layout.name }
    }

    override suspend fun setOpenCleaned(enabled: Boolean) {
        dataStore.edit { it[Keys.OPEN_CLEANED] = enabled }
    }

    override suspend fun setAutoCloseOnOpen(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_CLOSE_ON_OPEN] = enabled }
    }

    override suspend fun addCustomTrackingParam(name: String) {
        val n = name.trim().lowercase()
        if (n.isEmpty()) return
        dataStore.edit { p ->
            val cur = (p[Keys.CUSTOM_TRACKING] ?: "")
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            p[Keys.CUSTOM_TRACKING] = (cur + n).sorted().joinToString(",")
        }
    }

    override suspend fun removeCustomTrackingParam(name: String) {
        val n = name.trim().lowercase()
        dataStore.edit { p ->
            val cur = (p[Keys.CUSTOM_TRACKING] ?: "")
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            p[Keys.CUSTOM_TRACKING] = (cur - n).sorted().joinToString(",")
        }
    }

    override suspend fun excludeUrlFromHistory(url: String) {
        val u = url.trim()
        if (u.isEmpty()) return
        dataStore.edit { p ->
            val cur = (p[Keys.HISTORY_EXCLUSIONS] ?: "").split("\n").filter { it.isNotBlank() }
            if (u !in cur) p[Keys.HISTORY_EXCLUSIONS] = (cur + u).joinToString("\n")
        }
    }

    override suspend fun unexcludeUrlFromHistory(url: String) {
        val u = url.trim()
        dataStore.edit { p ->
            val cur = (p[Keys.HISTORY_EXCLUSIONS] ?: "").split("\n").filter { it.isNotBlank() }
            p[Keys.HISTORY_EXCLUSIONS] = cur.filter { it != u }.joinToString("\n")
        }
    }
}
