package com.dav3.linksentry.ui.inspect

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dav3.linksentry.domain.analyze.UrlAnalyzer
import com.dav3.linksentry.domain.model.DangerOverride
import com.dav3.linksentry.domain.model.DemoLinks
import com.dav3.linksentry.domain.model.DemoTour
import com.dav3.linksentry.domain.model.HandlerApp
import com.dav3.linksentry.domain.model.HistoryAction
import com.dav3.linksentry.domain.model.LinkCleanup
import com.dav3.linksentry.domain.model.ParamBehavior
import com.dav3.linksentry.domain.model.PseudoHandler
import com.dav3.linksentry.domain.model.Severity
import com.dav3.linksentry.domain.model.UrlFacts
import com.dav3.linksentry.domain.model.Verdict
import com.dav3.linksentry.domain.repository.DangerOverridesRepository
import com.dav3.linksentry.domain.repository.DemoKey
import com.dav3.linksentry.domain.repository.HandlerPrefsRepository
import com.dav3.linksentry.domain.repository.HistoryRepository
import com.dav3.linksentry.domain.repository.SettingsRepository
import com.dav3.linksentry.domain.system.BrowserRoleChecker
import com.dav3.linksentry.domain.system.HandlerRanker
import com.dav3.linksentry.domain.system.HandlerResolver
import com.dav3.linksentry.domain.system.HandlerUsage
import com.dav3.linksentry.domain.system.LinkActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One of three UI modes for the Inspect tab. */
sealed interface InspectUiState {
    /** Launcher mode: URL entry field + clipboard hint + role banner. */
    data class Manual(
        val input: String = "",
        val isDefaultBrowser: Boolean = false,
        val clipboardHasUrl: Boolean = false,
    ) : InspectUiState

    /** An inspected URL with analysis + handlers. */
    data class Inspect(
        val url: String,
        val facts: UrlFacts,
        val verdict: Verdict,
        val handlers: List<HandlerApp>,
        val cleanedUrl: String,
        /** Live cleanup state: what gets removed + the resulting URL. */
        val cleanup: LinkCleanup,
        /** Params the user opted to keep despite being tracking. */
        val keepParams: Set<String> = emptySet(),
        /** Params the user manually marked for removal (any name). */
        val removeParams: Set<String> = emptySet(),
        /** Param names saved as "always remove" across links. */
        val customTracking: Set<String> = emptySet(),
        /** User opted to keep the embedded credentials. */
        val keepCredentials: Boolean = false,
        /** When true, tapping a handler opens the cleaned URL. */
        val openCleaned: Boolean = false,
        /** Editable URL field: starts as the submitted URL; edits don't
         *  re-trigger analysis until the user resubmits. */
        val input: String = url,
        /**
         * DANGER-gate: when true the handler list is hidden behind an
         * explicit confirmation step. Cleared by a user green-light or by
         * a stored danger override (host or same-signals).
         */
        val dangerGate: Boolean = false,
        /** When non-null, openWith() waits for explicit user confirmation. */
        val confirmApp: HandlerApp? = null,
        /** True when a stored override silenced the gate for this link. */
        val overrideActive: Boolean = false,
        /** Non-null while the guided tour runs: current step + progress. */
        val tour: TourState? = null,
        /** User checked "always open over https" for this session. */
        val enforceHttps: Boolean = false,
        /** True when this exact URL is opted out of history recording. */
        val historyExcluded: Boolean = false,
        /** Launchable apps for the "search all apps" fallback (loaded on
         *  demand — empty until the user expands it). */
        val allApps: List<HandlerApp> = emptyList(),
        /** Current filter text for the fallback list. */
        val appSearch: String = "",
        /** Filtered fallback results (apps not already listed above). */
        val appSearchResults: List<HandlerApp> = emptyList(),
        /** True while the handler list / all-apps fallback is still
         *  resolving (analysis already on screen — see submitText). */
        val handlersLoading: Boolean = false,
    ) : InspectUiState

    /** Guided-tour progress; [index] is into [DemoTour.steps]. */
    data class TourState(
        val index: Int = 0,
        /** Set when a demo action fired; UI shows a transient notice. */
        val notice: String? = null,
    )

    /** Submitted text could not be parsed as a URL. */
    data class Invalid(val input: String) : InspectUiState
}

@HiltViewModel
class InspectViewModel @Inject constructor(
    private val resolver: HandlerResolver,
    private val actions: LinkActions,
    private val history: HistoryRepository,
    private val settingsRepo: SettingsRepository,
    private val roleChecker: BrowserRoleChecker,
    private val handlerPrefs: HandlerPrefsRepository,
    private val dangerOverrides: DangerOverridesRepository,
    private val demoRepo: com.dav3.linksentry.domain.repository.DemoRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<InspectUiState>(InspectUiState.Manual())
    val uiState: StateFlow<InspectUiState> = _uiState.asStateFlow()

    /**
     * Monotonic counter of "the Activity should finish now" requests.
     * Incremented after a successful handler launch (once history/pref
     * writes have landed — viewModelScope is cancelled on finish, so the
     * signal must be the LAST thing) and from clearAndClose(). The Activity
     * layer collects this and calls finish(); ViewModels never hold an
     * Activity reference.
     */
    private val _closeRequests = MutableStateFlow(0)
    val closeRequests: StateFlow<Int> = _closeRequests.asStateFlow()

    /**
     * Set when a link was opened but the app stays open (auto-close off):
     * the inspected link is dropped from the screen as the app loses
     * focus, so returning to LinkSentry never shows the last link.
     */
    private var clearOnPause = false

    init {
        // Immich-style "forced" intro: the tour starts by itself the very
        // first time the app is opened, walking every Inspect section with
        // fake data. Sandbox: nothing launches, nothing persists.
        viewModelScope.launch {
            if (!demoRepo.isSeen(DemoKey.TOUR)) {
                startTour()
            }
        }
    }

    /** Starts (or replays) the guided tour from step 0. */
    fun startTour() {
        viewModelScope.launch { applyTourStep(0) }
    }

    /** Advances the tour (Next / Skip). Ends: marks seen + back to Manual. */
    fun advanceTour() {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        val tour = state.tour ?: return
        val next = tour.index + 1
        if (next >= DemoTour.steps.size) {
            endTour(markSeen = true)
        } else {
            applyTourStep(next)
        }
    }

    /** Ends the tour early; [markSeen] false when only skipping for now. */
    fun skipTour() {
        endTour(markSeen = true)
    }

    private fun endTour(markSeen: Boolean) {
        _uiState.value = InspectUiState.Manual(isDefaultBrowser = roleChecker.isDefaultBrowser())
        if (markSeen) {
            viewModelScope.launch { demoRepo.markSeen(DemoKey.TOUR) }
        }
    }

    /** Loads [index]'s URL through the analyzer and attaches tour state. */
    private fun applyTourStep(index: Int) {
        viewModelScope.launch {
            val step = DemoTour.steps[index]
            val analysis = UrlAnalyzer.analyze(step.url)
            _uiState.value = InspectUiState.Inspect(
                url = analysis.facts.raw,
                facts = analysis.facts,
                verdict = analysis.verdict,
                handlers = DemoTour.fakeHandlers,
                cleanedUrl = UrlAnalyzer.cleanedUrl(analysis.facts),
                cleanup = UrlAnalyzer.cleanup(analysis.facts),
                input = analysis.facts.raw,
                dangerGate = analysis.verdict.worst == Severity.DANGER,
                tour = InspectUiState.TourState(index = index),
            )
        }
    }

    /** Shows a transient demo notice (tour sandbox). */
    private fun notice(message: String) {
        val state = _uiState.value
        if (state is InspectUiState.Inspect) {
            _uiState.value = state.copy(tour = state.tour?.copy(notice = message))
        }
    }

    /** Clears a transient demo notice. */
    fun clearNotice() {
        val state = _uiState.value
        if (state is InspectUiState.Inspect) {
            _uiState.value = state.copy(tour = state.tour?.copy(notice = null))
        }
    }

    fun submit(uri: Uri?) {
        if (uri == null) return
        submitText(uri.toString())
    }

    fun submitText(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val analysis = UrlAnalyzer.analyze(trimmed)
            if (analysis.facts.hasParseError) {
                _uiState.value = InspectUiState.Invalid(trimmed)
                return@launch
            }
            val dangerIds = analysis.verdict.signals
                .filter { it.severity == Severity.DANGER }
                .map { it.id }
                .toSet()
            val overrides = dangerOverrides.all()
            val hostOverride = overrides.any { it is DangerOverride.Host && it.host == analysis.facts.host }
            val signalsOverride = overrides.any {
                it is DangerOverride.Signals && it.ids == dangerIds && dangerIds.isNotEmpty()
            }
            val overrideActive = hostOverride || signalsOverride
            val settingsNow = settingsRepo.settings.first()
            val custom = settingsNow.customTrackingParams
            // PHASE 1 — the analysis goes on screen immediately. UrlAnalyzer
            // is pure Kotlin (sub-millisecond); PackageManager work (labels,
            // icons, all-apps enumeration) is what made the link appear late
            // together with the app list, so it moves to phase 2 below.
            _uiState.value = InspectUiState.Inspect(
                url = analysis.facts.raw,
                facts = analysis.facts,
                verdict = analysis.verdict,
                handlers = emptyList(),
                cleanedUrl = UrlAnalyzer.cleanedUrl(analysis.facts),
                cleanup = UrlAnalyzer.cleanup(analysis.facts, extraTracking = custom),
                openCleaned = settingsNow.openCleaned,
                customTracking = custom,
                dangerGate = analysis.verdict.worst == Severity.DANGER && !overrideActive,
                overrideActive = overrideActive,
                historyExcluded = analysis.facts.raw in settingsNow.historyExclusions,
                handlersLoading = true,
            )
            // Sandbox: regular inspections are recorded; tour navigation is
            // not (tour states are built by applyTourStep, which never records).
            // Demo links and user-excluded URLs never touch history.
            if (!DemoLinks.isDemo(analysis.facts.raw) &&
                analysis.facts.raw !in settingsNow.historyExclusions
            ) {
                history.record(
                    url = analysis.facts.raw,
                    host = analysis.facts.host,
                    severity = analysis.verdict.worst,
                    action = HistoryAction.INSPECTED,
                )
            }

            // PHASE 2 — handler resolution + always-visited app search data
            // fill in as a second emission; only the "Open with" section
            // waits for them. Skipped when the user already moved on.
            val handlers = buildHandlers(analysis.facts.raw, analysis.facts.host, analysis.facts.scheme)
            val allApps = if (DemoLinks.isDemo(analysis.facts.raw)) {
                emptyList() // tour/demo sandbox: no real app data
            } else {
                resolver.allLaunchableApps()
            }
            val current = _uiState.value
            if (current is InspectUiState.Inspect && current.url == analysis.facts.raw) {
                _uiState.value = current.copy(
                    handlers = handlers,
                    allApps = allApps,
                    handlersLoading = false,
                    // Keep any search the user already typed consistent.
                    appSearchResults = filterAppsForSearch(handlers, allApps, current.appSearch),
                )
            }
        }
    }

    fun openWith(app: HandlerApp) {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        // Tour sandbox: tapping a handler only explains what would happen.
        if (state.tour != null) {
            notice("Demo: would open in " + app.label + (if (app.isBrowser) " (browser)" else ""))
            return
        }
        // Pseudo entries (copy/share actions) never launch an app.
        when (app.packageName) {
            PseudoHandler.COPY -> {
                copyUrl()
                return
            }
            PseudoHandler.COPY_CLEANED -> {
                copyCleaned()
                return
            }
            PseudoHandler.SHARE -> {
                share()
                return
            }
        }
        // DANGER-gated links require explicit confirmation first.
        if (state.dangerGate) {
            _uiState.value = state.copy(confirmApp = app)
            return
        }
        launchAndRecord(state, app)
    }

    /** Confirm "open anyway" from the danger dialog. */
    fun confirmOpen(remember: Boolean) {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        val app = state.confirmApp ?: return
        _uiState.value = state.copy(confirmApp = null, dangerGate = false)
        launchAndRecord(state, app)
        if (remember) {
            viewModelScope.launch {
                val dangerIds = state.verdict.signals
                    .filter { it.severity == Severity.DANGER }
                    .map { it.id }
                    .toSet()
                dangerOverrides.grant(
                    DangerOverride.Signals(ids = dangerIds),
                )
            }
        }
    }

    /** User backed out of the danger confirmation. */
    fun cancelConfirm() {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        _uiState.value = state.copy(confirmApp = null)
    }

    /** "I trust this site" from the gate banner: reveals the list, stores nothing. */
    fun bypassGate() {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        if (state.tour != null) {
            // During the tour the gate can be revealed for exploration, but
            // nothing is ever persisted.
            _uiState.value = state.copy(dangerGate = false, tour = state.tour.copy(notice = "Demo: the app list is now revealed"))
        } else {
            _uiState.value = state.copy(dangerGate = false)
        }
    }

    /** Revoke the stored override from within a link view ("Previously
     *  allowed" note): next time this kind of link gates again. */
    fun revokeOverride() {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        if (!state.overrideActive) return
        viewModelScope.launch {
            dangerOverrides.all().forEach { override ->
                when (override) {
                    is DangerOverride.Host -> if (override.host == state.facts.host) dangerOverrides.revoke(override)
                    is DangerOverride.Signals -> {
                        val dangerIds = state.verdict.signals
                            .filter { it.severity == Severity.DANGER }
                            .map { it.id }
                            .toSet()
                        if (override.ids == dangerIds) dangerOverrides.revoke(override)
                    }
                }
            }
            // Re-gate immediately so the user sees the effect.
            _uiState.value = state.copy(dangerGate = state.verdict.worst == Severity.DANGER, overrideActive = false)
        }
    }

    /** "I trust this site, never warn again" from the gate banner. */
    fun trustHostForever() {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        _uiState.value = state.copy(dangerGate = false, overrideActive = true)
        viewModelScope.launch {
            dangerOverrides.grant(DangerOverride.Host(state.facts.host))
        }
    }

    private fun launchAndRecord(state: InspectUiState.Inspect, app: HandlerApp) {
        if (state.tour != null) return // unreachable: openWith sandboxes first
        // Enforce https: handlers open the upgraded URL when checked.
        val target = effectiveUrl(state)
        if (actions.openWith(app, target)) {
            viewModelScope.launch {
                // Persist usage so the picker reorders launcher-style:
                // domain preference outranks scheme-wide preference.
                handlerPrefs.recordUse(HandlerRanker.domainKey(state.facts.host), app.packageName)
                handlerPrefs.recordUse(HandlerRanker.schemeKey(state.facts.scheme), app.packageName)
                if (shouldRecord(state)) {
                    history.record(state.url, state.facts.host, state.verdict.worst, HistoryAction.OPENED_WITH)
                    // Remember the handler so History can re-open directly.
                    history.recordApp(state.url, app.packageName, app.activityName, app.label)
                }
                // Auto-close LAST: finish() cancels viewModelScope, so every
                // write above must already be done when this fires. Gated by
                // the "Close after opening" setting (Clear & close always
                // closes — it's an explicit exit). When staying open, mark
                // the link for removal at the next focus loss instead — but
                // only when it was actually SAVED to history (it's
                // recoverable from there). Demo links, per-link exclusions
                // and history-recording-off keep the link on screen: nothing
                // would remain of it otherwise.
                val settingsNow = settingsRepo.settings.first()
                if (settingsNow.autoCloseOnOpen) {
                    _closeRequests.value++
                } else {
                    clearOnPause = shouldRecord(state) && settingsNow.recordHistory
                }
            }
        }
    }

    /** The URL handlers actually open: cleaned / https-enforced / raw. */
    private fun effectiveUrl(state: InspectUiState.Inspect): String {
        var url = if (state.openCleaned) state.cleanup.url else state.url
        if (state.enforceHttps) {
            url = UrlAnalyzer.upgradeScheme(UrlAnalyzer.analyze(url).facts).url
        }
        return url
    }

    private fun shouldRecord(state: InspectUiState.Inspect): Boolean = !DemoLinks.isDemo(state.url) && !state.historyExcluded

    fun copyUrl() {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        if (state.tour != null) {
            notice("Demo: the link would be copied to the clipboard")
            return
        }
        actions.copy(state.url)
        viewModelScope.launch {
            handlerPrefs.recordUse(HandlerRanker.pseudoKey(state.facts.host), PseudoHandler.COPY)
            if (shouldRecord(state)) {
                history.record(state.url, state.facts.host, state.verdict.worst, HistoryAction.COPIED)
            }
        }
    }

    fun copyCleaned() {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        if (state.tour != null) {
            notice("Demo: the cleaned link would be copied")
            return
        }
        actions.copy(state.cleanup.url)
        viewModelScope.launch {
            handlerPrefs.recordUse(HandlerRanker.pseudoKey(state.facts.host), PseudoHandler.COPY_CLEANED)
            if (shouldRecord(state)) {
                history.record(state.url, state.facts.host, state.verdict.worst, HistoryAction.COPIED_CLEANED)
            }
        }
    }

    fun share() {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        if (state.tour != null) {
            notice("Demo: the Android share sheet would open")
            return
        }
        actions.share(effectiveUrl(state))
        viewModelScope.launch {
            handlerPrefs.recordUse(HandlerRanker.pseudoKey(state.facts.host), PseudoHandler.SHARE)
            if (shouldRecord(state)) {
                history.record(state.url, state.facts.host, state.verdict.worst, HistoryAction.SHARED)
            }
        }
    }

    fun reset() {
        // Keep the URL in the input field when it's valued (user asked:
        // "keeping the input when valued") — only the analysis is dropped.
        val kept = when (val current = _uiState.value) {
            is InspectUiState.Manual -> current.input
            is InspectUiState.Inspect -> current.input
            is InspectUiState.Invalid -> current.input
        }
        clearOnPause = false
        _uiState.value = InspectUiState.Manual(
            input = kept,
            isDefaultBrowser = roleChecker.isDefaultBrowser(),
        )
    }

    /**
     * Clear/close button: drop the analysis and ask the Activity to finish
     * (returns to whatever the user was doing before the link opened
     * LinkSentry). The state reset is synchronous so finish()-cancelled
     * coroutines can't leave a stale Inspect screen behind.
     */
    fun clearAndClose() {
        reset()
        _closeRequests.value++
    }

    /** Track the editable URL field in any mode. */
    fun onInputChange(input: String) {
        val current = _uiState.value
        _uiState.value = when (current) {
            is InspectUiState.Manual -> current.copy(input = input)
            // Editing while results are showing: update the field, keep the
            // analysis until the user resubmits.
            is InspectUiState.Inspect -> current.copy(input = input)
            is InspectUiState.Invalid -> InspectUiState.Manual(
                input = input,
                isDefaultBrowser = roleChecker.isDefaultBrowser(),
            )
        }
    }

    /** Re-check default-browser role (called on resume). */
    fun refreshRole() {
        val current = _uiState.value
        if (current is InspectUiState.Manual) {
            _uiState.value = current.copy(isDefaultBrowser = roleChecker.isDefaultBrowser())
        }
    }

    /**
     * Called as the app loses focus: if a link was opened while staying
     * open (auto-close off), drop it now — returning to LinkSentry must
     * never show the last opened link. Manual entry (nothing opened)
     * survives focus loss untouched.
     */
    fun onAppBackground() {
        if (clearOnPause) {
            clearOnPause = false
            reset()
        }
    }

    /**
     * Re-resolve the handler list for the inspected URL (called on resume).
     * The stored list is a point-in-time snapshot: apps get installed,
     * updated, or have their link-handling defaults flipped while
     * LinkSentry is in the background — this keeps "Open with" current.
     */
    fun refreshHandlers() {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        if (state.tour != null) return // tour is sandboxed; never resandbox data
        viewModelScope.launch {
            val ranked = rankHandlers(state)
            // Revalidate the all-apps cache (new installs/uninstalls pop
            // in) and use the fresh result for this screen.
            resolver.refreshAllAppsCache()
            val apps = resolver.allAppsCache.value
            val current = _uiState.value
            if (current is InspectUiState.Inspect && current.url == state.url) {
                _uiState.value = current.copy(
                    handlers = ranked,
                    allApps = apps,
                    // Keep active search results consistent with the new list.
                    appSearchResults = filterAppsForSearch(ranked, apps, current.appSearch),
                )
            }
        }
    }

    /** Shared builder: resolve + rank + pseudo entries for [state]. */
    private suspend fun rankHandlers(state: InspectUiState.Inspect): List<HandlerApp> = buildHandlers(state.url, state.facts.host, state.facts.scheme)

    /** Shared builder: resolve + rank + pseudo entries for a raw URL. */
    private suspend fun buildHandlers(url: String, host: String, scheme: String): List<HandlerApp> {
        val resolved = resolver.resolve(Uri.parse(url))
        val usage = handlerPrefs.observeAll().first()
        val ranked = HandlerRanker.rank(
            handlers = resolved,
            usage = usage,
            host = host,
            scheme = scheme,
        )
        return attachPseudoEntries(ranked, usage, host)
    }

    /** Copy/share actions ride along as pseudo entries, usage-ranked per
     *  domain like real apps so frequent copiers get them on top. */
    private fun attachPseudoEntries(ranked: List<HandlerApp>, usage: List<HandlerUsage>, host: String): List<HandlerApp> {
        val pseudoPkg = usage
            .filter { it.key == HandlerRanker.pseudoKey(host) }
            .maxByOrNull { it.lastUsed }?.packageName
        val copyEntries = listOf(
            HandlerApp(PseudoHandler.COPY, "", "Copy link", isBrowser = false, icon = null),
            HandlerApp(PseudoHandler.COPY_CLEANED, "", "Copy cleaned link", isBrowser = false, icon = null),
            HandlerApp(PseudoHandler.SHARE, "", "Share link", isBrowser = false, icon = null),
        )
        return if (pseudoPkg != null && copyEntries.any { it.packageName == pseudoPkg }) {
            listOf(copyEntries.first { it.packageName == pseudoPkg }) +
                ranked +
                copyEntries.filterNot { it.packageName == pseudoPkg }
        } else {
            ranked + copyEntries
        }
    }

    /** Open the system Default apps settings screen. */
    fun openBrowserSettings() {
        actions.openDefaultAppsSettings()
    }

    // ---------- cleanup interaction ----------

    private fun recompute(
        state: InspectUiState.Inspect,
        keepParams: Set<String> = state.keepParams,
        removeParams: Set<String> = state.removeParams,
        keepCredentials: Boolean = state.keepCredentials,
        customTracking: Set<String> = state.customTracking,
    ): LinkCleanup = UrlAnalyzer.cleanup(
        state.facts,
        keepParams,
        keepCredentials,
        removeParams,
        customTracking,
    )

    /**
     * Per-param toggle from the unified params list. One button, context-
     * aware action (fixes "cannot keep removed-by-default"):
     * - listed as removed → opt back in via keepParams (the only way to
     *   keep a stripped param; adding to removeParams would be a no-op)
     * - kept but manually removed → drop the manual removal
     * - kept by default → add a manual removal
     * - manually removed → keep it removed, drop from keepParams
     */
    fun toggleRemoveParam(name: String) {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        val behavior = UrlAnalyzer.paramBehavior(name, state.customTracking)
        // Truth = what the current cleanup actually did to this param
        // (listed as a removal AND not opted back in).
        val currentlyRemoved = state.cleanup.removals.any { it.token == name } &&
            name !in state.keepParams
        val (nextKeep, nextRemove) = if (currentlyRemoved) {
            // Stripped → opt back in via keepParams.
            state.keepParams + name to state.removeParams - name
        } else {
            when {
                behavior == ParamBehavior.KEEP ->
                    // Unknown param, currently kept → manual removal.
                    state.keepParams to state.removeParams + name
                else ->
                    // Tracker kept via keepParams → drop the opt-in so the
                    // default (strip) applies again.
                    state.keepParams - name to state.removeParams
            }
        }
        _uiState.value = state.copy(
            keepParams = nextKeep,
            removeParams = nextRemove,
            cleanup = recompute(state, nextKeep, nextRemove),
        )
    }

    /** Save a param name as "always remove" (user-taught tracker). */
    fun markParamAsTracking(name: String) {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        viewModelScope.launch {
            settingsRepo.addCustomTrackingParam(name)
            val custom = settingsRepo.settings.first().customTrackingParams
            _uiState.value = state.copy(
                customTracking = custom,
                cleanup = UrlAnalyzer.cleanup(state.facts, state.keepParams, state.keepCredentials, state.removeParams, custom),
            )
        }
    }

    /** Toggle "remove this" for one tracked param; recomputes the cleanup. */
    fun toggleKeepParam(name: String) {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        val next = if (name in state.keepParams) state.keepParams - name else state.keepParams + name
        _uiState.value = state.copy(
            keepParams = next,
            cleanup = recompute(state, keepParams = next),
        )
    }

    /** Toggle keeping the embedded credentials; recomputes the cleanup. */
    fun toggleKeepCredentials() {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        val next = !state.keepCredentials
        _uiState.value = state.copy(
            keepCredentials = next,
            cleanup = recompute(state, keepCredentials = next),
        )
    }

    /** Session toggle: handlers open the cleaned URL instead of the raw one. */
    fun toggleOpenCleaned() {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        _uiState.value = state.copy(openCleaned = !state.openCleaned)
    }

    /** Session toggle: handlers open an https-upgraded URL when checked. */
    fun toggleEnforceHttps() {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        _uiState.value = state.copy(enforceHttps = !state.enforceHttps)
    }

    /**
     * Per-link history opt-out (user: "an option to not store in history a
     * specific link"). Persists the exclusion; enabling also deletes any
     * existing rows for this exact URL so the past disappears with it.
     */
    fun toggleHistoryExcluded() {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        val next = !state.historyExcluded
        _uiState.value = state.copy(historyExcluded = next)
        viewModelScope.launch {
            if (next) {
                settingsRepo.excludeUrlFromHistory(state.url)
                history.deleteByUrl(state.url)
            } else {
                settingsRepo.unexcludeUrlFromHistory(state.url)
            }
        }
    }

    /** Reset this domain's handler sorting (per-domain reset). */
    fun resetDomainSorting() {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        viewModelScope.launch {
            handlerPrefs.clearKey(HandlerRanker.domainKey(state.facts.host))
            handlerPrefs.clearKey(HandlerRanker.pseudoKey(state.facts.host))
            // Re-rank without the wiped rows (pseudo entries re-attached).
            val reranked = rankHandlers(state)
            val current = _uiState.value
            if (current is InspectUiState.Inspect && current.url == state.url) {
                _uiState.value = current.copy(
                    handlers = reranked,
                    // Keep the search results consistent with the new list.
                    appSearchResults = filterAppsForSearch(reranked, current.allApps, current.appSearch),
                )
            }
        }
    }

    /**
     * Tracks the search text and recomputes the results. Filtering starts
     * at the first non-space character (whitespace-only input keeps the
     * default ranked list).
     */
    fun onAppSearchChange(query: String) {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        _uiState.value = state.copy(
            appSearch = query,
            appSearchResults = filterAppsForSearch(state.handlers, state.allApps, query),
        )
    }
}

/**
 * Search filter for the merged app list: current handlers (ranked order
 * preserved, pseudo entries excluded) followed by every other launchable
 * app, filtered case-insensitively on label or package name. A blank
 * (whitespace-only) query yields nothing — the default list shows.
 */
private fun filterAppsForSearch(
    handlers: List<HandlerApp>,
    allApps: List<HandlerApp>,
    query: String,
): List<HandlerApp> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    fun matches(app: HandlerApp) = app.label.contains(q, ignoreCase = true) || app.packageName.contains(q, ignoreCase = true)
    val seen = mutableSetOf<String>()
    return (handlers.filterNot { it.packageName.startsWith("@") } + allApps)
        .filter { seen.add(it.packageName) && matches(it) }
}
