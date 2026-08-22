package com.dav3.linksentry.domain.repository

import com.dav3.linksentry.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setRecordHistory(enabled: Boolean)

    suspend fun setRetentionDays(days: Int?)

    suspend fun setTheme(mode: com.dav3.linksentry.domain.model.ThemeMode)

    suspend fun setHandlerLayout(layout: com.dav3.linksentry.domain.model.HandlerLayout)

    suspend fun setOpenCleaned(enabled: Boolean)

    /** Remember a param name as "always remove" (user-taught tracker). */
    suspend fun addCustomTrackingParam(name: String)

    /** Forget a user-taught tracker param. */
    suspend fun removeCustomTrackingParam(name: String)
}
