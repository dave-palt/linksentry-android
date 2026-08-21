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
}
