package com.dav3.linksentry.ui.inspect

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dav3.linksentry.domain.analyze.UrlAnalyzer
import com.dav3.linksentry.domain.model.HandlerApp
import com.dav3.linksentry.domain.model.HistoryAction
import com.dav3.linksentry.domain.model.UrlFacts
import com.dav3.linksentry.domain.model.Verdict
import com.dav3.linksentry.domain.repository.HistoryRepository
import com.dav3.linksentry.domain.repository.SettingsRepository
import com.dav3.linksentry.domain.system.BrowserRoleChecker
import com.dav3.linksentry.domain.system.HandlerResolver
import com.dav3.linksentry.domain.system.LinkActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    ) : InspectUiState

    /** Submitted text could not be parsed as a URL. */
    data class Invalid(val input: String) : InspectUiState
}

@HiltViewModel
class InspectViewModel @Inject constructor(
    private val resolver: HandlerResolver,
    private val actions: LinkActions,
    private val history: HistoryRepository,
    settingsRepo: SettingsRepository,
    private val roleChecker: BrowserRoleChecker,
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
            val handlers = resolver.resolve(Uri.parse(analysis.facts.raw))
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
        _uiState.value = InspectUiState.Manual(isDefaultBrowser = roleChecker.isDefaultBrowser())
    }

    /** Track manual-mode text input. */
    fun onInputChange(input: String) {
        val current = _uiState.value
        _uiState.value = when (current) {
            is InspectUiState.Manual -> current.copy(input = input)
            else -> InspectUiState.Manual(
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
