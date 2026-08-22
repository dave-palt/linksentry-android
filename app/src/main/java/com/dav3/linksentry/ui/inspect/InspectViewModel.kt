package com.dav3.linksentry.ui.inspect

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dav3.linksentry.domain.analyze.UrlAnalyzer
import com.dav3.linksentry.domain.model.HandlerApp
import com.dav3.linksentry.domain.model.HistoryAction
import com.dav3.linksentry.domain.model.UrlFacts
import com.dav3.linksentry.domain.model.Verdict
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
        /** Editable URL field: starts as the submitted URL; edits don't
         *  re-trigger analysis until the user resubmits. */
        val input: String = url,
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
            val handlers = HandlerRanker.rank(
                handlers = resolved,
                usage = usage,
                host = analysis.facts.host,
                scheme = analysis.facts.scheme,
            )
            _uiState.value = InspectUiState.Inspect(
                url = analysis.facts.raw,
                facts = analysis.facts,
                verdict = analysis.verdict,
                handlers = handlers,
                cleanedUrl = UrlAnalyzer.cleanedUrl(analysis.facts),
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
        if (actions.openWith(app, state.url)) {
            viewModelScope.launch {
                // Persist usage so the picker reorders launcher-style:
                // domain preference outranks scheme-wide preference.
                handlerPrefs.recordUse(HandlerRanker.domainKey(state.facts.host), app.packageName)
                handlerPrefs.recordUse(HandlerRanker.schemeKey(state.facts.scheme), app.packageName)
                history.record(state.url, state.facts.host, state.verdict.worst, HistoryAction.OPENED_WITH)
            }
        }
    }

    fun copyUrl() {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        actions.copy(state.url)
        viewModelScope.launch {
            history.record(state.url, state.facts.host, state.verdict.worst, HistoryAction.COPIED)
        }
    }

    fun copyCleaned() {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        actions.copy(state.cleanedUrl)
        viewModelScope.launch {
            history.record(state.url, state.facts.host, state.verdict.worst, HistoryAction.COPIED_CLEANED)
        }
    }

    fun share() {
        val state = _uiState.value
        if (state !is InspectUiState.Inspect) return
        actions.share(state.url)
        viewModelScope.launch {
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
}
