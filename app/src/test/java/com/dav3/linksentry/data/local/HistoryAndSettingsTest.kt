package com.dav3.linksentry.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dav3.linksentry.domain.model.AppSettings
import com.dav3.linksentry.domain.model.HistoryAction
import com.dav3.linksentry.domain.model.Severity
import com.dav3.linksentry.domain.model.ThemeMode
import com.dav3.linksentry.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryAndSettingsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // ---------- DAO ----------

    @Test
    fun `dao insert and observe ordering`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, HistoryDatabase::class.java)
            .allowMainThreadQueries().build()
        val dao = db.historyDao()
        dao.insert(HistoryEntity(url = "https://b.com/", host = "b.com", severity = null, action = "INSPECTED", timestamp = 100))
        dao.insert(HistoryEntity(url = "https://a.com/", host = "a.com", severity = "DANGER", action = "OPENED_WITH", timestamp = 200))
        val all = dao.observeAll().first()
        assertEquals(2, all.size)
        assertEquals("a.com", all.first().host) // newest first
        db.close()
    }

    @Test
    fun `dao prunes older than cutoff`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, HistoryDatabase::class.java)
            .allowMainThreadQueries().build()
        val dao = db.historyDao()
        dao.insert(HistoryEntity(url = "u1", host = "a.com", severity = null, action = "INSPECTED", timestamp = 100))
        dao.insert(HistoryEntity(url = "u2", host = "b.com", severity = null, action = "INSPECTED", timestamp = 200))
        val deleted = dao.deleteOlderThan(150)
        assertEquals(1, deleted)
        assertEquals(1, dao.count())
        db.close()
    }

    @Test
    fun `dao clear`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, HistoryDatabase::class.java)
            .allowMainThreadQueries().build()
        val dao = db.historyDao()
        dao.insert(HistoryEntity(url = "u1", host = "a.com", severity = null, action = "INSPECTED", timestamp = 1))
        dao.clear()
        assertEquals(0, dao.count())
        db.close()
    }

    // ---------- HistoryRepositoryImpl (retention gating) ----------

    @Test
    fun `repository skips recording when history disabled`() = runTest {
        val dao = FakeDao()
        val settings = FakeSettingsRepo(AppSettings(recordHistory = false))
        val repo = HistoryRepositoryImpl(dao, settings)
        repo.record("https://a.com/", "a.com", null, HistoryAction.INSPECTED)
        assertTrue(dao.inserts.isEmpty())
    }

    @Test
    fun `repository records and prunes when enabled`() = runTest {
        val dao = FakeDao()
        val settings = FakeSettingsRepo(AppSettings())
        val repo = HistoryRepositoryImpl(dao, settings)
        repo.record("https://a.com/", "a.com", Severity.DANGER, HistoryAction.OPENED_WITH)
        assertEquals(1, dao.inserts.size)
        assertEquals(1, dao.pruneCalls)
    }

    // ---------- SettingsRepositoryImpl (DataStore round-trip) ----------

    @Test
    fun `settings default then round trip`() = runTest {
        val repo = SettingsRepositoryImpl(context)
        // defaults
        var s = repo.settings.first()
        assertEquals(true, s.recordHistory)
        assertEquals(30, s.retentionDays)
        assertEquals(ThemeMode.SYSTEM, s.theme)
        // write
        repo.setRecordHistory(false)
        repo.setRetentionDays(null)
        repo.setTheme(ThemeMode.DARK)
        repo.setHandlerLayout(com.dav3.linksentry.domain.model.HandlerLayout.GRID)
        s = repo.settings.first()
        assertEquals(false, s.recordHistory)
        assertEquals(null, s.retentionDays)
        assertEquals(ThemeMode.DARK, s.theme)
        assertEquals(com.dav3.linksentry.domain.model.HandlerLayout.GRID, s.handlerLayout)
    }

    // ---------- fakes ----------

    private class FakeDao : HistoryDao {
        val inserts = mutableListOf<HistoryEntity>()
        var pruneCalls = 0
        private val flow = kotlinx.coroutines.flow.MutableStateFlow<List<HistoryEntity>>(emptyList())

        override suspend fun insert(entry: HistoryEntity): Long {
            inserts.add(entry)
            return inserts.size.toLong()
        }

        override fun observeAll(): kotlinx.coroutines.flow.Flow<List<HistoryEntity>> = flow

        override suspend fun deleteOlderThan(cutoff: Long): Int {
            pruneCalls++
            return 0
        }

        override suspend fun clear(): Int = 0

        override suspend fun deleteById(id: Long) {}

        override suspend fun deleteByUrl(url: String): Int {
            val before = inserts.size
            inserts.removeAll { it.url == url }
            return before - inserts.size
        }

        override suspend fun count(): Int = inserts.size

        override suspend fun bumpOpened(url: String, ts: Long, severity: String?): Int = 0

        override suspend fun bumpAction(url: String, ts: Long, action: String, severity: String?): Int = 0

        override suspend fun touchInspect(url: String, severity: String?): Int = 0

        override suspend fun updateLastApp(url: String, pkg: String, activity: String?, label: String?): Int = 0

        override fun search(pattern: String): kotlinx.coroutines.flow.Flow<List<HistoryEntity>> = flow

        override suspend fun deleteByPattern(pattern: String): Int = 0
    }

    private class FakeSettingsRepo(private var s: AppSettings) : SettingsRepository {
        override val settings: kotlinx.coroutines.flow.Flow<AppSettings> =
            kotlinx.coroutines.flow.MutableStateFlow(s)

        override suspend fun setRecordHistory(enabled: Boolean) {
            s = s.copy(recordHistory = enabled)
        }

        override suspend fun setRetentionDays(days: Int?) {
            s = s.copy(retentionDays = days)
        }

        override suspend fun setTheme(mode: ThemeMode) {
            s = s.copy(theme = mode)
        }

        override suspend fun setHandlerLayout(layout: com.dav3.linksentry.domain.model.HandlerLayout) {}

        override suspend fun setOpenCleaned(enabled: Boolean) {}

        override suspend fun addCustomTrackingParam(name: String) {}

        override suspend fun removeCustomTrackingParam(name: String) {}

        override suspend fun excludeUrlFromHistory(url: String) {}

        override suspend fun unexcludeUrlFromHistory(url: String) {}
    }

    @Test
    fun `history exclusion round trip`() = runTest {
        val repo = SettingsRepositoryImpl(context)
        repo.excludeUrlFromHistory("https://example.com/private?a=1")
        repo.excludeUrlFromHistory("https://other.org/x")
        assertEquals(
            setOf("https://example.com/private?a=1", "https://other.org/x"),
            repo.settings.first().historyExclusions,
        )
        repo.unexcludeUrlFromHistory("https://example.com/private?a=1")
        assertEquals(setOf("https://other.org/x"), repo.settings.first().historyExclusions)
    }

    @Test
    fun `repository deleteByUrl removes only that row`() = runTest {
        val dao = FakeDao()
        val repo = HistoryRepositoryImpl(dao, FakeSettingsRepo(AppSettings()))
        repo.record("https://a.com/1", "a.com", null, HistoryAction.INSPECTED)
        repo.record("https://b.org/2", "b.org", null, HistoryAction.INSPECTED)
        val removed = repo.deleteByUrl("https://a.com/1")
        assertEquals(1, removed)
        assertEquals(listOf("https://b.org/2"), dao.inserts.map { it.url })
    }
}
