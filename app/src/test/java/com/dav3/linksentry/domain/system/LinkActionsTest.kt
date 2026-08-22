package com.dav3.linksentry.domain.system

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.dav3.linksentry.domain.model.HandlerApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinkActionsTest {

    private val handler = HandlerApp(
        packageName = "com.android.chrome",
        activityName = "com.google.android.apps.chrome.Main",
        label = "Chrome",
        isBrowser = true,
    )

    @Test
    fun `open intent is explicit with component set`() {
        val intent = AndroidLinkActions.buildOpenIntent(handler, "https://example.com/x")
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://example.com/x", intent.dataString)
        assertEquals("com.android.chrome", intent.component?.packageName)
        assertEquals("com.google.android.apps.chrome.Main", intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `open intent is never implicit`() {
        val intent = AndroidLinkActions.buildOpenIntent(handler, "https://example.com/")
        assertNotNull(intent.component)
    }

    @Test
    fun `share fires a chooser intent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val actions = AndroidLinkActions(context)
        actions.share("https://example.com/")
        val app = context.applicationContext as android.app.Application
        val started = Shadows.shadowOf(app).nextStartedActivity
        assertNotNull(started)
    }
}
