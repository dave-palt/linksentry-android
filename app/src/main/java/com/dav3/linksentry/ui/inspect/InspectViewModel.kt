package com.dav3.linksentry.ui.inspect

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dav3.linksentry.domain.analyze.UrlAnalyzer
import com.dav3.linksentry.domain.model.DangerOverride
import com.dav3.linksentry.domain.model.HandlerApp
import com.dav3.linksentry.domain.model.HistoryAction
import com.dav3.linksentry.domain.model.LinkCleanup
import com.dav3.linksentry.domain.model.ParamBehavior
import com.dav3.linksentry.domain.model.PseudoHandler
import com.dav3.linksentry.domain.model.Severity
import com.dav3.linksentry.domain.model.UrlFacts
import com.dav3.linksentry.domain.model.Verdict
import com.dav3.linksentry.domain.repository.DangerOverridesRepository
import com.dav3.linksentry.domain.repository.HandlerPrefsRepository
import com.dav3.linksentry.domain.repository.HistoryRepository
import com.dav3.linksentry.domain.repository.SettingsRepository
import com.dav3.linksentry.domain.system.BrowserRoleChecker
import com.dav3.linksentry.domain.system.HandlerRanker
import com.dav3.linksentry.domain.system.HandlerResolver
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
    ) : InspectUiState

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
) : ViewModel() {

    private val _uiState = MutableStateFlow<InspectUiState>(InspectUiState.Manual())
    val uiState: StateFlow<InspectUiState> = _uiState.asStateFlow()

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
            val resolved = resolver.resolve(Uri.parse(analysis.facts.raw))
            // Launcher-style ordering: user's most-recently/most-used apps
            // for this domain first, then per-scheme, then browsers A→Z.
            val usage = handlerPrefs.observeAll().first()
            val ranked = HandlerRanker.rank(
                handlers = resolved,
                usage = usage,
                host = analysis.facts.host,
                scheme = analysis.facts.scheme,
            )
            // Copy actions ride along as pseudo entries, usage-ranked per
            // domain like real apps so frequent copiers get them on top.
            val pseudoPkg = usage
                .filter { it.key == HandlerRanker.pseudoKey(analysis.facts.host) }
                .maxByOrNull { it.lastUsed }?.packageName
            val copyEntries = listOf(
                HandlerApp(PseudoHandler.COPY, "", "Copy link", isBrowser = false, icon = null),
                HandlerApp(PseudoHandler.COPY_CLEANED, "", "Copy cleaned link", isBrowser = false, icon = null),
                HandlerApp(PseudoHandler.SHARE, "", "Share link", isBrowser = false, icon = null),
            )
            val handlers = if (pseudoPkg != null && copyEntries.any { it.packageName == pseudoPkg }) {
                listOf(copyEntries.first { it.packageName == pseudoPkg }) +
                    ranked +
                    copyEntries.filterNot { it.packageName == pseudoPkg }
            } else {
                ranked + copyEntries
            }
            // Danger gate: only for DANGER-severity links without a stored
            // user green-light (per host, or for links with identical DANGER
            // signals — "same link types are not flagged anymore").
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
            _uiState.value = InspectUiState.Inspect(
                url = analysis.facts.raw,
                facts = analysis.facts,
                verdict = analysis.verdict,
                handlers = handlers,
                cleanedUrl = UrlAnalyzer.cleanedUrl(analysis.facts),
                cleanup = UrlAnalyzer.cleanup(analysis.facts, extraTracking = custom),
                openCleaned = settingsNow.openCleaned,
                customTracking = custom,
                dangerGate = analysis.verdict.worst == Severity.DANGER && !overrideActive,
                overrideActive = overrideActive,
            )
            history.record(
                url = analysis.facts.raw,
                host = analysis.facts.host,
                severity = analysis.verdict.worst,
                action = HistoryAction.INSPECTED,
            )
        }
    }

    fun openWith(app: HandlerApp) {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
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
        _uiState.value = state.copy(dangerGate = false)
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
        val target = if (state.openCleaned) state.cleanup.url else state.url
        if (actions.openWith(app, target)) {
            viewModelScope.launch {
                // Persist usage so the picker reorders launcher-style:
                // domain preference outranks scheme-wide preference.
                handlerPrefs.recordUse(HandlerRanker.domainKey(state.facts.host), app.packageName)
                handlerPrefs.recordUse(HandlerRanker.schemeKey(state.facts.scheme), app.packageName)
                history.record(state.url, state.facts.host, state.verdict.worst, HistoryAction.OPENED_WITH)
                // Remember the handler so History can re-open directly.
                history.recordApp(state.url, app.packageName, app.activityName, app.label)
            }
        }
    }

    fun copyUrl() {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        actions.copy(state.url)
        viewModelScope.launch {
            handlerPrefs.recordUse(HandlerRanker.pseudoKey(state.facts.host), PseudoHandler.COPY)
            history.record(state.url, state.facts.host, state.verdict.worst, HistoryAction.COPIED)
        }
    }

    fun copyCleaned() {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        actions.copy(state.cleanup.url)
        viewModelScope.launch {
            handlerPrefs.recordUse(HandlerRanker.pseudoKey(state.facts.host), PseudoHandler.COPY_CLEANED)
            history.record(state.url, state.facts.host, state.verdict.worst, HistoryAction.COPIED_CLEANED)
        }
    }

    fun share() {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        actions.share(if (state.openCleaned) state.cleanup.url else state.url)
        viewModelScope.launch {
            handlerPrefs.recordUse(HandlerRanker.pseudoKey(state.facts.host), PseudoHandler.SHARE)
            history.record(state.url, state.facts.host, state.verdict.worst, HistoryAction.SHARED)
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
        _uiState.value = InspectUiState.Manual(
            input = kept,
            isDefaultBrowser = roleChecker.isDefaultBrowser(),
        )
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

    /** Reset this domain's handler sorting (per-domain reset). */
    fun resetDomainSorting() {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        viewModelScope.launch {
            handlerPrefs.clearKey(HandlerRanker.domainKey(state.facts.host))
            handlerPrefs.clearKey(HandlerRanker.pseudoKey(state.facts.host))
            // Re-rank without the wiped rows.
            val usage = handlerPrefs.observeAll().first()
            _uiState.value = state.copy(
                handlers = HandlerRanker.rank(resolver.resolve(Uri.parse(state.url)), usage, state.facts.host, state.facts.scheme),
            )
        }
    }
}
