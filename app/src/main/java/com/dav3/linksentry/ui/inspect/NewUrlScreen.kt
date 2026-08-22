package com.dav3.linksentry.ui.inspect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
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
        // Seed from whatever is on screen: an inspected link arrives as
        // Inspect.input, the manual tab as Manual.input. Editing must KEEP
        // the current link (user: "allow to edit the url keeping the input
        // when valued") — never blank when a link is already there.
        val seed = when (val s = state) {
            is InspectUiState.Manual -> s.input
            is InspectUiState.Inspect -> s.input
            is InspectUiState.Invalid -> s.input
        }
        var text by remember(seed) { mutableStateOf(seed) }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Edit the link or paste a new one. It stays on this device — nothing is opened until you choose an app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Multi-line textarea: long URLs stay readable instead of
            // scrolling away in one thin line.
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("https://…") },
                singleLine = false,
                minLines = 4,
                maxLines = 10,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(
                    onClick = {
                        if (text.isNotBlank()) {
                            viewModel.submitText(text)
                            onSubmitted()
                        }
                    },
                    enabled = text.isNotBlank(),
                ) {
                    Text("Inspect link")
                }
                if (text.isNotEmpty()) {
                    androidx.compose.material3.TextButton(onClick = { text = "" }) {
                        Text("Clear")
                    }
                }
            }
        }
    }
}
