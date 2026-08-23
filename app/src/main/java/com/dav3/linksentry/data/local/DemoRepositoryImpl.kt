package com.dav3.linksentry.data.local

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.dav3.linksentry.domain.repository.DemoKey
import com.dav3.linksentry.domain.repository.DemoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** First-open demo flags in the shared app DataStore. */
@Singleton
class DemoRepositoryImpl @Inject constructor(
    @ApplicationContext context: android.content.Context,
) : DemoRepository {

    private val dataStore = context.appDataStore

    private fun key(key: DemoKey) = booleanPreferencesKey("demo_seen_" + key.name.lowercase())

    override fun observe(key: DemoKey): Flow<Boolean> = dataStore.data.map { it[this.key(key)] ?: false }

    override suspend fun isSeen(key: DemoKey): Boolean = observe(key).first()

    override suspend fun markSeen(key: DemoKey) {
        dataStore.edit { it[this.key(key)] = true }
    }

    override suspend fun unmarkSeen(key: DemoKey) {
        dataStore.edit { it[this.key(key)] = false }
    }
}
