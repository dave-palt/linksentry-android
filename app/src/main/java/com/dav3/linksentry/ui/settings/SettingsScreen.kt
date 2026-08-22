package com.dav3.linksentry.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dav3.linksentry.domain.model.AppSettings
import com.dav3.linksentry.domain.model.ThemeMode
import com.dav3.linksentry.domain.repository.SettingsRepository
import com.dav3.linksentry.domain.system.BrowserRoleChecker
import com.dav3.linksentry.domain.system.LinkActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    roleChecker: BrowserRoleChecker,
    private val actions: LinkActions,
) : ViewModel() {
    val settings = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun setRecordHistory(enabled: Boolean) {
        viewModelScope.launch { repository.setRecordHistory(enabled) }
    }

    fun setRetentionDays(days: Int?) {
        viewModelScope.launch { repository.setRetentionDays(days) }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { repository.setTheme(mode) }
    }

    fun openDefaultAppsSettings() = actions.openDefaultAppsSettings()
}

@Composable
fun SettingsScreen(
    isDefaultBrowser: Boolean,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    SettingsContent(
        settings = settings,
        isDefaultBrowser = isDefaultBrowser,
        onSetRecordHistory = viewModel::setRecordHistory,
        onSetRetention = viewModel::setRetentionDays,
        onSetTheme = viewModel::setTheme,
        onOpenDefaultAppsSettings = viewModel::openDefaultAppsSettings,
    )
}

/** State-driven body — no ViewModel, previewable and screenshot-testable. */
@Composable
fun SettingsContent(
    settings: AppSettings,
    isDefaultBrowser: Boolean,
    onSetRecordHistory: (Boolean) -> Unit,
    onSetRetention: (Int?) -> Unit,
    onSetTheme: (ThemeMode) -> Unit,
    onOpenDefaultAppsSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (isDefaultBrowser) "LinkSentry is your default browser" else "LinkSentry is not your default browser",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "As default browser, every link you tap opens here first for inspection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onOpenDefaultAppsSettings) {
                    Text(if (isDefaultBrowser) "Change default browser" else "Set as default browser")
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Record history", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Keep a local list of links you've inspected. Stored only on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.recordHistory, onCheckedChange = onSetRecordHistory)
        }

        if (settings.recordHistory) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Keep history for", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RetentionChip("1 day", 1, settings, onSetRetention)
                    RetentionChip("7 days", 7, settings, onSetRetention)
                    RetentionChip("30 days", 30, settings, onSetRetention)
                    RetentionChip("Forever", null, settings, onSetRetention)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Theme", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChip("System", ThemeMode.SYSTEM, settings, onSetTheme)
                ThemeChip("Light", ThemeMode.LIGHT, settings, onSetTheme)
                ThemeChip("Dark", ThemeMode.DARK, settings, onSetTheme)
            }
        }
    }
}

@Composable
private fun RetentionChip(
    label: String,
    days: Int?,
    settings: AppSettings,
    onSetRetention: (Int?) -> Unit,
) {
    FilterChip(
        selected = settings.retentionDays == days,
        onClick = { onSetRetention(days) },
        label = { Text(label) },
    )
}

@Composable
private fun ThemeChip(
    label: String,
    mode: ThemeMode,
    settings: AppSettings,
    onSetTheme: (ThemeMode) -> Unit,
) {
    FilterChip(
        selected = settings.theme == mode,
        onClick = { onSetTheme(mode) },
        label = { Text(label) },
    )
}
