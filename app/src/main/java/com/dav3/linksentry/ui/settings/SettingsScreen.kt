package com.dav3.linksentry.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    private val handlerPrefs: com.dav3.linksentry.domain.repository.HandlerPrefsRepository,
    private val dangerOverridesRepo: com.dav3.linksentry.domain.repository.DangerOverridesRepository,
) : ViewModel() {
    val settings = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    /** Stored danger overrides ("don't warn again") for the list view. */
    val dangerOverrides = dangerOverridesRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun revokeOverride(override: com.dav3.linksentry.domain.model.DangerOverride) {
        viewModelScope.launch { dangerOverridesRepo.revoke(override) }
    }

    fun setRecordHistory(enabled: Boolean) {
        viewModelScope.launch { repository.setRecordHistory(enabled) }
    }

    fun setRetentionDays(days: Int?) {
        viewModelScope.launch { repository.setRetentionDays(days) }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { repository.setTheme(mode) }
    }

    fun setHandlerLayout(layout: com.dav3.linksentry.domain.model.HandlerLayout) {
        viewModelScope.launch { repository.setHandlerLayout(layout) }
    }

    fun resetHandlerSorting() {
        viewModelScope.launch { handlerPrefs.clearAll() }
    }

    fun setOpenCleaned(enabled: Boolean) {
        viewModelScope.launch { repository.setOpenCleaned(enabled) }
    }

    fun openDefaultAppsSettings() = actions.openDefaultAppsSettings()
}

@Composable
fun SettingsScreen(
    isDefaultBrowser: Boolean,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val overrides by viewModel.dangerOverrides.collectAsState()
    SettingsContent(
        settings = settings,
        isDefaultBrowser = isDefaultBrowser,
        onSetRecordHistory = viewModel::setRecordHistory,
        onSetRetention = viewModel::setRetentionDays,
        onSetTheme = viewModel::setTheme,
        onOpenDefaultAppsSettings = viewModel::openDefaultAppsSettings,
        onSetHandlerLayout = viewModel::setHandlerLayout,
        onSetOpenCleaned = viewModel::setOpenCleaned,
        onResetSorting = viewModel::resetHandlerSorting,
        dangerOverrides = overrides,
        onRevokeOverride = viewModel::revokeOverride,
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
    onSetHandlerLayout: (com.dav3.linksentry.domain.model.HandlerLayout) -> Unit = {},
    onSetOpenCleaned: (Boolean) -> Unit = {},
    onResetSorting: () -> Unit = {},
    dangerOverrides: List<com.dav3.linksentry.domain.model.DangerOverride> = emptyList(),
    onRevokeOverride: (com.dav3.linksentry.domain.model.DangerOverride) -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
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

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Open cleaned links", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Handlers open the cleaned URL (credentials and tracking removed) by default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.openCleaned, onCheckedChange = onSetOpenCleaned)
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Reset app ordering", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Forget which apps you prefer for which sites. Sorting starts fresh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onResetSorting) {
                Text("Reset")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Handler list layout", style = MaterialTheme.typography.bodyLarge)
            Text(
                "How the \"Open with\" picker is laid out.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LayoutChip("List", com.dav3.linksentry.domain.model.HandlerLayout.LIST, settings, onSetHandlerLayout)
                LayoutChip("Grid", com.dav3.linksentry.domain.model.HandlerLayout.GRID, settings, onSetHandlerLayout)
            }
        }

        // ---- Allowed dangerous links ("don't warn again" overrides) ----
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Allowed dangerous links", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Links you gave a green light. Delete to be warned again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (dangerOverrides.isEmpty()) {
                Text(
                    "Nothing here yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            dangerOverrides.forEach { override ->
                val label = when (override) {
                    is com.dav3.linksentry.domain.model.DangerOverride.Host -> "Site: ${override.host}"
                    is com.dav3.linksentry.domain.model.DangerOverride.Signals -> "Link type: ${override.ids.joinToString(", ") { it.name }}"
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRevokeOverride(override) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete override",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LayoutChip(
    label: String,
    layout: com.dav3.linksentry.domain.model.HandlerLayout,
    settings: AppSettings,
    onSetHandlerLayout: (com.dav3.linksentry.domain.model.HandlerLayout) -> Unit,
) {
    FilterChip(
        selected = settings.handlerLayout == layout,
        onClick = { onSetHandlerLayout(layout) },
        label = { Text(label) },
    )
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
