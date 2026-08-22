package com.dav3.linksentry.ui.inspect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * "Inspect another link" page: full-screen URL entry. Submitting pops back
 * to the Inspect tab, where the new analysis replaces the old one; system
 * back (or the up button) keeps the original analysis untouched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewUrlScreen(
    onSubmitted: () -> Unit,
    viewModel: InspectViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inspect another link") },
                navigationIcon = {
                    IconButton(onClick = onSubmitted) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Paste or type a URL to inspect. The link stays on this device — nothing is opened until you choose an app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = (state as? InspectUiState.Manual)?.input ?: "",
                onValueChange = viewModel::onInputChange,
                placeholder = { Text("https://…") },
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.material3.Button(
                onClick = {
                    val s = state
                    if (s is InspectUiState.Manual && s.input.isNotBlank()) {
                        viewModel.submitText(s.input)
                        onSubmitted()
                    }
                },
                enabled = (state as? InspectUiState.Manual)?.input?.isNotBlank() == true,
            ) {
                Text("Inspect link")
            }
        }
    }
}
