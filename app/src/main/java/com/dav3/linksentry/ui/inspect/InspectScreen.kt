package com.dav3.linksentry.ui.inspect

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
fun InspectScreen(
    initialUrl: String?,
    onUrlInspected: () -> Unit = {},
    snackbarMessage: String? = null,
    onSnackbarShown: () -> Unit = {},
    onInspectNew: () -> Unit = {},
    handlerLayout: com.dav3.linksentry.domain.model.HandlerLayout = com.dav3.linksentry.domain.model.HandlerLayout.LIST,
    replayTour: Boolean = false,
    onTourRestarted: () -> Unit = {},
    viewModel: InspectViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // Replay request from Settings (NavHost flag).
    LaunchedEffect(replayTour) {
        if (replayTour) {
            viewModel.startTour()
            onTourRestarted()
        }
    }

    // Consume a VIEW intent URL exactly once.
    LaunchedEffect(initialUrl) {
        if (initialUrl != null) {
            viewModel.submit(android.net.Uri.parse(initialUrl))
            onUrlInspected()
        }
    }

    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            snackbar.showSnackbar(snackbarMessage)
            onSnackbarShown()
        }
    }

    // Re-check the default-browser role AND re-resolve the handler list on
    // every resume: apps get installed/updated and link-handling defaults
    // get flipped while LinkSentry is in the background — the "Open with"
    // list must reflect the device's current state, not submit-time.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshRole()
        viewModel.refreshHandlers()
        onPauseOrDispose { }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        // The outer NavHost Scaffold already consumes system-bar insets;
        // zero them here to avoid double status-bar padding (the tall
        // header the user reported). Lists receive the status-bar inset
        // via their contentPadding instead — content scrolls edge-to-edge.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        InspectContent(
            state = state,
            onOpenApp = viewModel::openWith,
            onCopy = viewModel::copyUrl,
            onCopyCleaned = viewModel::copyCleaned,
            onShare = viewModel::share,
            onReinspect = viewModel::reset,
            onManualInput = viewModel::onInputChange,
            onSubmitDemo = viewModel::submitText,
            onSubmitManual = {
                val s = state
                if (s is InspectUiState.Manual) viewModel.submitText(s.input)
            },
            onOpenBrowserSettings = viewModel::openBrowserSettings,
            onInspectNew = onInspectNew,
            handlerLayout = handlerLayout,
            onToggleKeepParam = viewModel::toggleKeepParam,
            onToggleKeepCredentials = viewModel::toggleKeepCredentials,
            onToggleOpenCleaned = viewModel::toggleOpenCleaned,
            onToggleRemoveParam = viewModel::toggleRemoveParam,
            onMarkParamAsTracking = viewModel::markParamAsTracking,
            onResetSorting = viewModel::resetDomainSorting,
            onEditUrl = viewModel::submitText,
            onBypassGate = viewModel::bypassGate,
            onTrustHostForever = viewModel::trustHostForever,
            onConfirmOpen = viewModel::confirmOpen,
            onCancelConfirm = viewModel::cancelConfirm,
            onRevokeOverride = viewModel::revokeOverride,
            onAdvanceTour = viewModel::advanceTour,
            onSkipTour = viewModel::skipTour,
            onToggleEnforceHttps = viewModel::toggleEnforceHttps,
            onToggleHistoryExcluded = viewModel::toggleHistoryExcluded,
            onAppSearchChange = viewModel::onAppSearchChange,
            modifier = Modifier.padding(padding),
        )
    }
}
