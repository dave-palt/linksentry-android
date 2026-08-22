package com.dav3.linksentry.ui.inspect

import android.net.Uri
import com.dav3.linksentry.domain.model.AppSettings
import com.dav3.linksentry.domain.model.HandlerApp
import com.dav3.linksentry.domain.model.HistoryAction
import com.dav3.linksentry.domain.model.LinkRecord
import com.dav3.linksentry.domain.model.Severity
import com.dav3.linksentry.domain.repository.HistoryRepository
import com.dav3.linksentry.domain.repository.SettingsRepository
import com.dav3.linksentry.domain.system.BrowserRoleChecker
import com.dav3.linksentry.domain.system.HandlerResolver
import com.dav3.linksentry.domain.system.LinkActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InspectViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val chrome = HandlerApp(
        packageName = "com.android.chrome",
        activityName = "chrome.Main",
        label = "Chrome",
        isBrowser = true,
    )
    private val youtube = HandlerApp(
        packageName = "com.google.android.youtube",
        activityName = "yt.Main",
        label = "YouTube",
        isBrowser = false,
    )

    private class FakeResolver(var apps: List<HandlerApp> = emptyList()) : HandlerResolver {
        var lastUri: Uri? = null
        override suspend fun resolve(uri: Uri): List<HandlerApp> {
            lastUri = uri
            return apps
        }
    }

    private class FakeLinkActions : LinkActions {
        val opened = mutableListOf<Pair<HandlerApp, String>>()
        val copied = mutableListOf<String>()
        var shared: String? = null
        var settingsOpened = false
        override fun openWith(app: HandlerApp, url: String): Boolean {
            opened.add(app to url)
            return true
        }
        override fun share(url: String) {
            shared = url
        }
        override fun copy(text: String) {
            copied.add(text)
        }
        override fun openDefaultAppsSettings() {
            settingsOpened = true
        }
    }

    private class FakeHistory(private val settings: SettingsRepository) : HistoryRepository {
        val records = mutableListOf<Triple<String, String, HistoryAction>>()
        override fun observeAll(): kotlinx.coroutines.flow.Flow<List<LinkRecord>> = MutableStateFlow(emptyList())
        override suspend fun record(url: String, host: String, severity: Severity?, action: HistoryAction) {
            // Mimic the real repo contract: skip recording when disabled.
            if (!settings.settings.first().recordHistory) return
            records.add(Triple(url, host, action))
        }
        override suspend fun clear() {}
    }

    private class FakeSettings(s: AppSettings = AppSettings()) : SettingsRepository {
        override val settings = MutableStateFlow(s)
        override suspend fun setRecordHistory(enabled: Boolean) {}
        override suspend fun setRetentionDays(days: Int?) {}
        override suspend fun setTheme(mode: com.dav3.linksentry.domain.model.ThemeMode) {}
    }

    private class FakeRoleChecker(var held: Boolean = false) : BrowserRoleChecker {
        override fun isDefaultBrowser() = held
    }

    private lateinit var resolver: FakeResolver
    private lateinit var actions: FakeLinkActions
    private lateinit var history: FakeHistory
    private lateinit var settingsRepo: FakeSettings
    private lateinit var role: FakeRoleChecker

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        resolver = FakeResolver(listOf(chrome, youtube))
        actions = FakeLinkActions()
        settingsRepo = FakeSettings()
        history = FakeHistory(settingsRepo)
        role = FakeRoleChecker()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = InspectViewModel(resolver, actions, history, settingsRepo, role)

    @Test
    fun initial_state_is_Manual() = runTest(dispatcher) {
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value is InspectUiState.Manual)
    }

    @Test
    fun submit_uri_inspects_and_records() = runTest(dispatcher) {
        val vm = vm()
        vm.submit(Uri.parse("http://google.com@evil.com/x"))
        dispatcher.scheduler.advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is InspectUiState.Inspect)
        state as InspectUiState.Inspect
        assertEquals("evil.com", state.facts.host)
        assertEquals(Severity.DANGER, state.verdict.worst)
        assertEquals(2, state.handlers.size)
        assertEquals(1, history.records.size)
        assertEquals(HistoryAction.INSPECTED, history.records.first().third)
    }

    @Test
    fun garbage_text_yields_Invalid() = runTest(dispatcher) {
        val vm = vm()
        vm.submitText("total garbage $$ ::")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value is InspectUiState.Invalid)
    }

    @Test
    fun history_disabled_skips_recording() = runTest(dispatcher) {
        settingsRepo.settings.value = AppSettings(recordHistory = false)
        val vm = vm()
        vm.submit(Uri.parse("https://example.com/"))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, history.records.size)
    }

    @Test
    fun openWith_launches_and_records() = runTest(dispatcher) {
        val vm = vm()
        vm.submit(Uri.parse("https://example.com/"))
        dispatcher.scheduler.advanceUntilIdle()
        vm.openWith(chrome)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, actions.opened.size)
        assertEquals("com.android.chrome", actions.opened.first().first.packageName)
        assertEquals(1, history.records.count { it.third == HistoryAction.OPENED_WITH })
    }

    @Test
    fun copy_actions_copy_expected_strings() = runTest(dispatcher) {
        val vm = vm()
        vm.submit(Uri.parse("https://a.com/p?utm_source=x&id=2"))
        dispatcher.scheduler.advanceUntilIdle()
        vm.copyUrl()
        vm.copyCleaned()
        assertEquals(2, actions.copied.size)
        assertEquals("https://a.com/p?utm_source=x&id=2", actions.copied[0])
        assertEquals("https://a.com/p?id=2", actions.copied[1])
    }

    @Test
    fun share_sends_original_url() = runTest(dispatcher) {
        val vm = vm()
        vm.submit(Uri.parse("https://a.com/p?utm_source=x"))
        dispatcher.scheduler.advanceUntilIdle()
        vm.share()
        assertEquals("https://a.com/p?utm_source=x", actions.shared)
    }

    @Test
    fun refreshRole_picks_up_role_change() = runTest(dispatcher) {
        val vm = vm()
        // Not default initially.
        assertFalse((vm.uiState.value as InspectUiState.Manual).isDefaultBrowser)
        // User grants the role in system settings, then returns to the app.
        role.held = true
        vm.refreshRole()
        assertTrue((vm.uiState.value as InspectUiState.Manual).isDefaultBrowser)
    }
}
