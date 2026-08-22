package com.dav3.linksentry.ui.inspect

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
    viewModel: InspectViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

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

    // Re-check the default-browser role on every resume so the Manual-mode
    // banner reflects reality after the user returns from system Settings.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshRole()
        onPauseOrDispose { }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        InspectContent(
            state = state,
            onOpenApp = viewModel::openWith,
            onCopy = viewModel::copyUrl,
            onCopyCleaned = viewModel::copyCleaned,
            onShare = viewModel::share,
            onReinspect = viewModel::reset,
            onManualInput = viewModel::onInputChange,
            onSubmitManual = {
                val s = state
                if (s is InspectUiState.Manual) viewModel.submitText(s.input)
            },
            onOpenBrowserSettings = viewModel::openBrowserSettings,
            onInspectNew = onInspectNew,
            handlerLayout = handlerLayout,
            modifier = Modifier.padding(padding),
        )
    }
}
