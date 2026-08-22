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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

        override suspend fun delete(id: Long) {}

        override fun search(query: String): kotlinx.coroutines.flow.Flow<List<LinkRecord>> = MutableStateFlow(emptyList())

        override suspend fun deleteFound(query: String): Int = 0

        override suspend fun recordApp(url: String, pkg: String, activity: String?, label: String?) {}
    }

    private class FakeSettings(s: AppSettings = AppSettings()) : SettingsRepository {
        override val settings = MutableStateFlow(s)
        override suspend fun setRecordHistory(enabled: Boolean) {}
        override suspend fun setRetentionDays(days: Int?) {}
        override suspend fun setTheme(mode: com.dav3.linksentry.domain.model.ThemeMode) {}
        override suspend fun setHandlerLayout(layout: com.dav3.linksentry.domain.model.HandlerLayout) {}
        override suspend fun setOpenCleaned(enabled: Boolean) {}

        private val customTracking = mutableSetOf<String>()

        override suspend fun addCustomTrackingParam(name: String) {
            customTracking.add(name.trim().lowercase())
            settings.value = settings.value.copy(customTrackingParams = customTracking.toSet())
        }

        override suspend fun removeCustomTrackingParam(name: String) {
            customTracking.remove(name.trim().lowercase())
            settings.value = settings.value.copy(customTrackingParams = customTracking.toSet())
        }
    }

    private class FakeDangerOverrides : com.dav3.linksentry.domain.repository.DangerOverridesRepository {
        val granted = mutableListOf<com.dav3.linksentry.domain.model.DangerOverride>()

        override fun observeAll() = kotlinx.coroutines.flow.MutableStateFlow(granted.toList())

        override suspend fun all(): List<com.dav3.linksentry.domain.model.DangerOverride> = granted.toList()

        override suspend fun grant(override: com.dav3.linksentry.domain.model.DangerOverride) {
            granted.add(override)
        }

        override suspend fun clearAll() = granted.clear()

        override suspend fun revoke(override: com.dav3.linksentry.domain.model.DangerOverride) {
            granted.remove(override)
        }
    }

    private class FakeHandlerPrefs : com.dav3.linksentry.domain.repository.HandlerPrefsRepository {
        val uses = mutableListOf<Pair<String, String>>()
        var cleared = false
        val clearedKeys = mutableListOf<String>()
        override fun observeAll() = MutableStateFlow(
            // Model Room's DELETE: cleared keys no longer appear.
            uses.filter { it.first !in clearedKeys }
                .map { com.dav3.linksentry.domain.system.HandlerUsage(it.first, it.second, 1, 0) },
        )
        override suspend fun forKey(key: String) = emptyList<com.dav3.linksentry.domain.system.HandlerUsage>()
        override suspend fun recordUse(key: String, pkg: String) {
            uses.add(key to pkg)
        }
        override suspend fun forget(key: String, pkg: String) {}
        override suspend fun clearAll() {
            cleared = true
        }
        override suspend fun clearKey(key: String) {
            clearedKeys.add(key)
        }
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
        handlerPrefs = FakeHandlerPrefs()
        dangerOverrides = FakeDangerOverrides()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private lateinit var handlerPrefs: FakeHandlerPrefs
    private lateinit var dangerOverrides: FakeDangerOverrides

    private fun vm() = InspectViewModel(resolver, actions, history, settingsRepo, role, handlerPrefs, dangerOverrides)

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
        // 2 real handlers + 3 pseudo entries (copy, copy-cleaned, share).
        assertEquals(5, state.handlers.size)
        assertEquals(
            listOf("@copy", "@copy-cleaned", "@share"),
            state.handlers.map { it.packageName }.filter { it.startsWith("@") },
        )
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
        // Usage persisted for BOTH domain and scheme keys.
        assertEquals(
            listOf("domain:example.com", "scheme:https"),
            handlerPrefs.uses.map { it.first },
        )
    }

    @Test
    fun toggle_keep_param_recomputes_cleanup() = runTest(dispatcher) {
        val vm = vm()
        vm.submitText("https://a.com/p?utm_source=x&id=2")
        dispatcher.scheduler.advanceUntilIdle()
        val st = vm.uiState.value as InspectUiState.Inspect
        assertEquals("https://a.com/p?id=2", st.cleanup.url)
        vm.toggleKeepParam("utm_source")
        val st2 = vm.uiState.value as InspectUiState.Inspect
        assertEquals("https://a.com/p?utm_source=x&id=2", st2.cleanup.url)
        assertEquals(setOf("utm_source"), st2.keepParams)
    }

    @Test
    fun toggle_open_cleaned_changes_open_target() = runTest(dispatcher) {
        val vm = vm()
        vm.submitText("https://a.com/p?utm_source=x&id=2")
        dispatcher.scheduler.advanceUntilIdle()
        val st = vm.uiState.value as InspectUiState.Inspect
        assertFalse(st.openCleaned)
        vm.openWith(chrome)
        assertEquals("https://a.com/p?utm_source=x&id=2", actions.opened.first().second)
        vm.toggleOpenCleaned()
        vm.openWith(chrome)
        assertEquals("https://a.com/p?id=2", actions.opened[1].second)
    }

    @Test
    fun openWith_pseudo_copy_copies_and_records() = runTest(dispatcher) {
        val vm = vm()
        vm.submitText("https://a.com/p")
        dispatcher.scheduler.advanceUntilIdle()
        val copyEntry = (vm.uiState.value as InspectUiState.Inspect).handlers.first { it.packageName == "@copy" }
        vm.openWith(copyEntry)
        dispatcher.scheduler.advanceUntilIdle()
        // No app launched, no OPENED_WITH history — it's a copy action.
        assertEquals(0, actions.opened.size)
        assertEquals(1, history.records.count { it.third == HistoryAction.COPIED })
        assertEquals(listOf("pseudo:a.com" to "@copy"), handlerPrefs.uses)
    }

    @Test
    fun most_used_copy_entry_floats_to_top() = runTest(dispatcher) {
        handlerPrefs.uses.add("pseudo:a.com" to "@copy-cleaned")
        val vm = vm()
        vm.submitText("https://a.com/p")
        dispatcher.scheduler.advanceUntilIdle()
        val first = (vm.uiState.value as InspectUiState.Inspect).handlers.first()
        assertEquals("@copy-cleaned", first.packageName)
    }

    @Test
    fun resetDomainSorting_clears_and_reranks() = runTest(dispatcher) {
        handlerPrefs.uses.add("domain:a.com" to "com.google.android.youtube")
        val vm = vm()
        vm.submitText("https://a.com/p")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("com.google.android.youtube", (vm.uiState.value as InspectUiState.Inspect).handlers.first().packageName)
        vm.resetDomainSorting()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("domain:a.com", "pseudo:a.com"), handlerPrefs.clearedKeys)
        // After clearing, ranking falls back to browsers-first alpha.
        val first = (vm.uiState.value as InspectUiState.Inspect).handlers.first { !it.packageName.startsWith("@") }
        assertEquals("com.android.chrome", first.packageName)
    }

    @Test
    fun danger_link_is_gated_and_needs_confirm() = runTest(dispatcher) {
        val vm = vm()
        vm.submitText("http://user@evil.com/x")
        dispatcher.scheduler.advanceUntilIdle()
        val st = vm.uiState.value as InspectUiState.Inspect
        assertTrue(st.dangerGate)
        // Gate is UI-only: handlers exist, openWith defers to confirm.
        vm.openWith(st.handlers.first { !it.packageName.startsWith("@") })
        val st2 = vm.uiState.value as InspectUiState.Inspect
        assertNotNull(st2.confirmApp)
        assertEquals(0, actions.opened.size)
        vm.cancelConfirm()
        assertNull((vm.uiState.value as InspectUiState.Inspect).confirmApp)
    }

    @Test
    fun confirm_open_with_remember_never_warns_again_for_same_signals() = runTest(dispatcher) {
        val vm = vm()
        vm.submitText("http://user@evil.com/x")
        dispatcher.scheduler.advanceUntilIdle()
        val app = (vm.uiState.value as InspectUiState.Inspect).handlers.first { !it.packageName.startsWith("@") }
        vm.openWith(app)
        vm.confirmOpen(remember = true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, actions.opened.size)
        assertEquals(1, dangerOverrides.granted.size)

        // Same danger shape elsewhere: no gate this time.
        vm.submitText("http://user@other-evil.com/y")
        dispatcher.scheduler.advanceUntilIdle()
        val st3 = vm.uiState.value as InspectUiState.Inspect
        assertFalse(st3.dangerGate)
        assertTrue(st3.overrideActive)
    }

    @Test
    fun keep_button_works_on_removed_by_default_params() = runTest(dispatcher) {
        val vm = vm()
        vm.submitText("https://a.com/p?utm_source=x&q=1")
        dispatcher.scheduler.advanceUntilIdle()
        val st = vm.uiState.value as InspectUiState.Inspect
        assertTrue(st.cleanup.url.endsWith("q=1")) // utm_source stripped

        vm.toggleRemoveParam("utm_source") // "Keep"
        dispatcher.scheduler.advanceUntilIdle()
        val st2 = vm.uiState.value as InspectUiState.Inspect
        assertTrue("expected utm_source back: ${st2.cleanup.url}", st2.cleanup.url.contains("utm_source=x"))
        assertEquals(setOf("utm_source"), st2.keepParams)
    }

    @Test
    fun keep_then_remove_again_restrips_param() = runTest(dispatcher) {
        val vm = vm()
        vm.submitText("https://a.com/p?utm_source=x&q=1")
        dispatcher.scheduler.advanceUntilIdle()
        vm.toggleRemoveParam("utm_source") // keep
        vm.toggleRemoveParam("utm_source") // remove again
        dispatcher.scheduler.advanceUntilIdle()
        val st = vm.uiState.value as InspectUiState.Inspect
        assertFalse(st.cleanup.url.contains("utm_source"))
    }

    @Test
    fun manual_remove_of_default_keep_param_strips_it() = runTest(dispatcher) {
        val vm = vm()
        vm.submitText("https://a.com/p?q=1&z=2")
        dispatcher.scheduler.advanceUntilIdle()
        vm.toggleRemoveParam("q") // default-keep → manual remove
        dispatcher.scheduler.advanceUntilIdle()
        val st = vm.uiState.value as InspectUiState.Inspect
        assertFalse(st.cleanup.url.contains("q="))
        assertTrue(st.cleanup.url.contains("z=2"))
    }

    @Test
    fun toggling_keep_does_not_forget_manual_removals() = runTest(dispatcher) {
        val vm = vm()
        vm.submitText("https://a.com/p?utm_source=x&trackid=9")
        dispatcher.scheduler.advanceUntilIdle()
        vm.toggleRemoveParam("trackid") // manual remove
        vm.toggleRemoveParam("utm_source") // keep (keepParams opt-in)
        dispatcher.scheduler.advanceUntilIdle()
        val st = vm.uiState.value as InspectUiState.Inspect
        assertFalse(st.cleanup.url.contains("trackid"))
        assertTrue(st.cleanup.url.contains("utm_source"))
    }

    @Test
    fun mark_param_as_tracking_updates_cleanup_and_persists() = runTest(dispatcher) {
        val vm = vm()
        vm.submitText("https://a.com/p?trackid=123&q=1")
        dispatcher.scheduler.advanceUntilIdle()
        // Not a known tracker: cleanup ignores it initially.
        val st = vm.uiState.value as InspectUiState.Inspect
        assertTrue(st.cleanup.removals.none { it.token == "trackid" })

        vm.markParamAsTracking("trackid")
        dispatcher.scheduler.advanceUntilIdle()
        val st2 = vm.uiState.value as InspectUiState.Inspect
        assertEquals("trackid", st2.customTracking.firstOrNull())
        assertTrue(st2.cleanup.removals.any { it.token == "trackid" })
        assertEquals("https://a.com/p?q=1", st2.cleanup.url)
    }

    @Test
    fun revoke_override_re_gates_immediately() = runTest(dispatcher) {
        val vm = vm()
        vm.submitText("http://user@evil.com/x")
        dispatcher.scheduler.advanceUntilIdle()
        vm.trustHostForever()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue((vm.uiState.value as InspectUiState.Inspect).overrideActive)

        // Revoke from the link view: gate comes back at once.
        vm.revokeOverride()
        dispatcher.scheduler.advanceUntilIdle()
        val st = vm.uiState.value as InspectUiState.Inspect
        assertFalse(st.overrideActive)
        assertTrue(st.dangerGate)
        assertEquals(0, dangerOverrides.granted.size)
    }

    @Test
    fun trust_host_forever_grants_host_override() = runTest(dispatcher) {
        val vm = vm()
        vm.submitText("http://user@evil.com/x")
        dispatcher.scheduler.advanceUntilIdle()
        vm.trustHostForever()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, dangerOverrides.granted.size)
        // Resubmit same host: gate stays open with note.
        vm.submitText("http://user@evil.com/x2")
        dispatcher.scheduler.advanceUntilIdle()
        val st = vm.uiState.value as InspectUiState.Inspect
        assertFalse(st.dangerGate)
        assertTrue(st.overrideActive)
    }

    @Test
    fun info_link_is_not_gated() = runTest(dispatcher) {
        val vm = vm()
        vm.submitText("https://a.com/p?utm_source=x")
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse((vm.uiState.value as InspectUiState.Inspect).dangerGate)
    }

    @Test
    fun share_pseudo_entry_shares() = runTest(dispatcher) {
        val vm = vm()
        vm.submitText("https://a.com/p")
        dispatcher.scheduler.advanceUntilIdle()
        val shareEntry = (vm.uiState.value as InspectUiState.Inspect).handlers.first { it.packageName == "@share" }
        vm.openWith(shareEntry)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, history.records.count { it.third == HistoryAction.SHARED })
        assertEquals(0, actions.opened.size)
    }

    @Test
    fun edit_url_via_hero_resubmits() = runTest(dispatcher) {
        val vm = vm()
        vm.submitText("https://a.com/p")
        dispatcher.scheduler.advanceUntilIdle()
        vm.submitText("https://a.com/edited")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("https://a.com/edited", (vm.uiState.value as InspectUiState.Inspect).url)
    }

    @Test
    fun prior_usage_floats_preferred_app_to_top() = runTest(dispatcher) {
        handlerPrefs.uses.add("domain:example.com" to "com.google.android.youtube")
        val vm = vm()
        vm.submit(Uri.parse("https://example.com/"))
        dispatcher.scheduler.advanceUntilIdle()
        val state = vm.uiState.value as InspectUiState.Inspect
        assertEquals("com.google.android.youtube", state.handlers.first().packageName)
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

    @Test
    fun submit_populates_inspect_input_with_url() = runTest(dispatcher) {
        val vm = vm()
        vm.submit(Uri.parse("https://example.com/p?id=2"))
        dispatcher.scheduler.advanceUntilIdle()
        val state = vm.uiState.value as InspectUiState.Inspect
        assertEquals("https://example.com/p?id=2", state.input)
    }

    @Test
    fun editing_input_in_Inspect_keeps_analysis_until_resubmit() = runTest(dispatcher) {
        val vm = vm()
        vm.submit(Uri.parse("https://example.com/p?id=2"))
        dispatcher.scheduler.advanceUntilIdle()
        // User edits the URL field — analysis stays live until they resubmit.
        vm.onInputChange("https://example.com/other")
        dispatcher.scheduler.advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is InspectUiState.Inspect)
        state as InspectUiState.Inspect
        assertEquals("https://example.com/other", state.input)
        assertEquals("example.com", state.facts.host)
    }

    @Test
    fun resubmitting_edited_input_reanalyses() = runTest(dispatcher) {
        val vm = vm()
        vm.submit(Uri.parse("https://example.com/p"))
        dispatcher.scheduler.advanceUntilIdle()
        vm.onInputChange("https://other.org/q")
        vm.submitText("https://other.org/q")
        dispatcher.scheduler.advanceUntilIdle()
        val state = vm.uiState.value as InspectUiState.Inspect
        assertEquals("other.org", state.facts.host)
        assertEquals(2, history.records.size)
    }

    @Test
    fun reset_keeps_input_when_valued() = runTest(dispatcher) {
        val vm = vm()
        vm.submit(Uri.parse("https://example.com/p"))
        dispatcher.scheduler.advanceUntilIdle()
        vm.reset()
        val state = vm.uiState.value
        assertTrue(state is InspectUiState.Manual)
        assertEquals("https://example.com/p", (state as InspectUiState.Manual).input)
    }

    @Test
    fun submit_while_Inspect_showing_replaces_analysis() = runTest(dispatcher) {
        val vm = vm()
        vm.submit(Uri.parse("https://example.com/p"))
        dispatcher.scheduler.advanceUntilIdle()
        // A second VIEW intent while results are on screen.
        vm.submit(Uri.parse("http://***@evil.com/x"))
        dispatcher.scheduler.advanceUntilIdle()
        val state = vm.uiState.value as InspectUiState.Inspect
        assertEquals("evil.com", state.facts.host)
    }
}
