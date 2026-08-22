package com.dav3.linksentry.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryDaoV4Test {

    private fun db(): HistoryDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        HistoryDatabase::class.java,
    ).build()

    @Test
    fun `inspect never inflates open count or clobbers opened state`() = runTest {
        val db = db()
        val dao = db.historyDao()
        try {
            dao.insert(HistoryEntity(url = "https://a.com/p", host = "a.com", severity = null, action = "INSPECTED", timestamp = 1, openCount = 0))
            // open once -> count 1, action OPENED_WITH
            dao.bumpOpened("https://a.com/p", ts = 2, severity = null)
            // re-inspect -> severity refreshed only
            assertEquals(1, dao.touchInspect("https://a.com/p", "WARN"))
            dao.bumpAction("https://a.com/p", ts = 3, action = "COPIED", severity = "WARN")
            val rows = dao.observeAll().first()
            assertEquals(1, rows.size)
            assertEquals(1, rows[0].openCount)
            assertEquals("COPIED", rows[0].action)
            assertEquals("WARN", rows[0].severity)
        } finally {
            db.close()
        }
    }

    @Test
    fun `opened bumps count and keeps latest timestamp`() = runTest {
        val db = db()
        val dao = db.historyDao()
        try {
            dao.insert(HistoryEntity(url = "https://b.com", host = "b.com", severity = null, action = "INSPECTED", timestamp = 1, openCount = 0))
            assertEquals(1, dao.bumpOpened("https://b.com", ts = 10, severity = null))
            assertEquals(1, dao.bumpOpened("https://b.com", ts = 20, severity = null))
            val row = dao.observeAll().first()[0]
            assertEquals(2, row.openCount)
            assertEquals(20, row.timestamp)
        } finally {
            db.close()
        }
    }

    @Test
    fun `dao search matches url and host`() = runTest {
        val db = db()
        val dao = db.historyDao()
        try {
            dao.insert(HistoryEntity(url = "https://wiki.org/x?utm_source=n", host = "wiki.org", severity = null, action = "INSPECTED", timestamp = 1))
            dao.insert(HistoryEntity(url = "https://shop.io/y?item=2", host = "shop.io", severity = null, action = "INSPECTED", timestamp = 2))
            assertEquals(1, dao.search("%utm_source%").first().size)
            assertEquals(1, dao.search("%shop.io%").first().size)
            assertEquals(0, dao.search("%zzz%").first().size)
            assertEquals(2, dao.search("%%").first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `dao deleteByPattern removes only matches`() = runTest {
        val db = db()
        val dao = db.historyDao()
        try {
            dao.insert(HistoryEntity(url = "https://wiki.org/x?utm_source=n", host = "wiki.org", severity = null, action = "INSPECTED", timestamp = 1))
            dao.insert(HistoryEntity(url = "https://shop.io/y?item=2", host = "shop.io", severity = null, action = "INSPECTED", timestamp = 2))
            assertEquals(1, dao.deleteByPattern("%wiki%"))
            assertEquals(1, dao.count())
        } finally {
            db.close()
        }
    }

    @Test
    fun `dao updateLastApp persists handler for direct reopen`() = runTest {
        val db = db()
        val dao = db.historyDao()
        try {
            dao.insert(HistoryEntity(url = "https://a.com/p", host = "a.com", severity = null, action = "OPENED_WITH", timestamp = 1))
            assertEquals(1, dao.updateLastApp("https://a.com/p", "com.firefox", "org.mozilla/Browser", "Firefox"))
            val row = dao.observeAll().first()[0]
            assertEquals("com.firefox", row.lastAppPackage)
            assertEquals("org.mozilla/Browser", row.lastAppActivity)
            assertEquals("Firefox", row.lastAppLabel)
        } finally {
            db.close()
        }
    }
}
