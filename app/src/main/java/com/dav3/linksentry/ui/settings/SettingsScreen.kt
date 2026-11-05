package com.dav3.linksentry.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.flow.first
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
    private val demoRepo: com.dav3.linksentry.domain.repository.DemoRepository,
    private val layaStore: com.dav3.linksentry.data.laya.LayaModelStore,
    private val layaRuntime: com.dav3.linksentry.domain.laya.LayaRuntime,
) : ViewModel() {

    /** First-open tip; null until the flag is read. */
    val demoTip = kotlinx.coroutines.flow.MutableStateFlow<Boolean?>(null)

    /** Laya model install/import UI state. */
    val layaUi = kotlinx.coroutines.flow.MutableStateFlow(LayaSettingsUi())

    init {
        viewModelScope.launch {
            demoTip.value = !demoRepo.isSeen(com.dav3.linksentry.domain.repository.DemoKey.SETTINGS)
        }
        refreshLaya()
    }

    /** Re-reads the model bundle state from disk. */
    fun refreshLaya(keepMessage: Boolean = true) {
        val message = if (keepMessage) layaUi.value.message else null
        layaUi.value = when (val status = layaStore.status()) {
            is com.dav3.linksentry.data.laya.LayaModelStatus.Installed ->
                LayaSettingsUi(install = LayaInstallUi.INSTALLED, totalBytes = status.totalBytes, message = message)
            is com.dav3.linksentry.data.laya.LayaModelStatus.Corrupt ->
                LayaSettingsUi(install = LayaInstallUi.CORRUPT, mismatched = status.mismatched, message = message)
            com.dav3.linksentry.data.laya.LayaModelStatus.NotInstalled ->
                LayaSettingsUi(install = LayaInstallUi.NOT_INSTALLED, message = message)
        }
    }

    /** Toggles the verdict feature; stopping the engine frees its memory. */
    fun setLayaEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setLayaVerdictEnabled(enabled)
            if (!enabled) layaRuntime.stop()
        }
    }

    /** Imports SAF-picked files; only a complete, hash-verified bundle lands. */
    fun importLayaModel(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            layaUi.value = layaUi.value.copy(busy = true, message = "Verifying and copying files…")
            val result = layaStore.import(uris)
            val enabled = repository.settings.first().layaVerdictEnabled
            if (result.ok) {
                layaRuntime.restart(enabled)
                refreshLaya(keepMessage = false)
                layaUi.value = layaUi.value.copy(
                    busy = false,
                    message = "Model installed. Verdicts appear on newly inspected links.",
                )
            } else {
                refreshLaya(keepMessage = false)
                layaUi.value = layaUi.value.copy(busy = false, message = result.message)
            }
        }
    }

    /** Deletes the imported bundle and stops the engine. */
    fun deleteLayaModel() {
        layaStore.delete()
        layaRuntime.restart(enabled = false)
        refreshLaya(keepMessage = false)
    }

    fun dismissTip() {
        demoTip.value = false
        viewModelScope.launch { demoRepo.markSeen(com.dav3.linksentry.domain.repository.DemoKey.SETTINGS) }
    }

    /** Replay the intro tour next time Inspect opens (or a manual trigger). */
    fun replayTour() {
        viewModelScope.launch { demoRepo.unmarkSeen(com.dav3.linksentry.domain.repository.DemoKey.TOUR) }
    }
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

    fun setAutoCloseOnOpen(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoCloseOnOpen(enabled) }
    }

    fun openDefaultAppsSettings() = actions.openDefaultAppsSettings()
}

@Composable
fun SettingsScreen(
    isDefaultBrowser: Boolean,
    onReplayTour: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val overrides by viewModel.dangerOverrides.collectAsState()
    val demoTip by viewModel.demoTip.collectAsState()
    val layaUi by viewModel.layaUi.collectAsState()
    val layaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.importLayaModel(uris) }
    SettingsContent(
        demoTip = demoTip == true,
        onDismissTip = viewModel::dismissTip,
        onReplayTour = {
            viewModel.replayTour()
            onReplayTour()
        },
        settings = settings,
        isDefaultBrowser = isDefaultBrowser,
        onSetRecordHistory = viewModel::setRecordHistory,
        onSetRetention = viewModel::setRetentionDays,
        onSetTheme = viewModel::setTheme,
        onOpenDefaultAppsSettings = viewModel::openDefaultAppsSettings,
        onSetHandlerLayout = viewModel::setHandlerLayout,
        onSetOpenCleaned = viewModel::setOpenCleaned,
        onSetAutoCloseOnOpen = viewModel::setAutoCloseOnOpen,
        onResetSorting = viewModel::resetHandlerSorting,
        dangerOverrides = overrides,
        onRevokeOverride = viewModel::revokeOverride,
        laya = layaUi,
        layaEnabled = settings.layaVerdictEnabled,
        onSetLayaEnabled = viewModel::setLayaEnabled,
        onImportLayaModel = {
            viewModel.refreshLaya()
            layaPicker.launch(arrayOf("*/*"))
        },
        onDeleteLayaModel = viewModel::deleteLayaModel,
    )
}

/** State-driven body — no ViewModel, previewable and screenshot-testable. */
@Composable
fun SettingsContent(
    settings: AppSettings,
    isDefaultBrowser: Boolean,
    demoTip: Boolean = false,
    onDismissTip: () -> Unit = {},
    onReplayTour: () -> Unit = {},
    onSetRecordHistory: (Boolean) -> Unit,
    onSetRetention: (Int?) -> Unit,
    onSetTheme: (ThemeMode) -> Unit,
    onOpenDefaultAppsSettings: () -> Unit,
    onSetHandlerLayout: (com.dav3.linksentry.domain.model.HandlerLayout) -> Unit = {},
    onSetOpenCleaned: (Boolean) -> Unit = {},
    onSetAutoCloseOnOpen: (Boolean) -> Unit = {},
    onResetSorting: () -> Unit = {},
    dangerOverrides: List<com.dav3.linksentry.domain.model.DangerOverride> = emptyList(),
    onRevokeOverride: (com.dav3.linksentry.domain.model.DangerOverride) -> Unit = {},
    laya: LayaSettingsUi = LayaSettingsUi(),
    layaEnabled: Boolean = true,
    onSetLayaEnabled: (Boolean) -> Unit = {},
    onImportLayaModel: () -> Unit = {},
    onDeleteLayaModel: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                top = androidx.compose.foundation.layout.WindowInsets.statusBars
                    .asPaddingValues()
                    .calculateTopPadding() + 20.dp,
                end = 20.dp,
                bottom = 20.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)

        if (demoTip) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Demo — this is Settings", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Tune recording, theme, handler layout and trusted links. Everything is stored on this device only.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismissTip) { Text("Got it") }
                        OutlinedButton(onClick = onReplayTour) { Text("Replay intro tour") }
                    }
                }
            }
        }

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
                Text("Close after opening", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "LinkSentry closes itself (and leaves recents) as soon as a link opens in your chosen app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.autoCloseOnOpen, onCheckedChange = onSetAutoCloseOnOpen)
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
                LayoutButton(
                    label = "List",
                    description = "Rows with app names",
                    icon = { Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null) },
                    layout = com.dav3.linksentry.domain.model.HandlerLayout.LIST,
                    settings = settings,
                    onSetHandlerLayout = onSetHandlerLayout,
                )
                LayoutButton(
                    label = "Grid",
                    description = "Compact icon tiles",
                    icon = { Icon(Icons.Filled.GridView, contentDescription = null) },
                    layout = com.dav3.linksentry.domain.model.HandlerLayout.GRID,
                    settings = settings,
                    onSetHandlerLayout = onSetHandlerLayout,
                )
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

        // ---- Optional on-device Laya verdict ----
        LayaSection(
            laya = laya,
            layaEnabled = layaEnabled,
            onSetLayaEnabled = onSetLayaEnabled,
            onImportLayaModel = onImportLayaModel,
            onDeleteLayaModel = onDeleteLayaModel,
        )
    }
}

/** Install shape of the optional Laya model bundle, for UI copy. */
enum class LayaInstallUi { NOT_INSTALLED, INSTALLED, CORRUPT }

/** State-driven UI model of the Laya section (previewable without a VM). */
data class LayaSettingsUi(
    val install: LayaInstallUi = LayaInstallUi.NOT_INSTALLED,
    val totalBytes: Long = 0,
    val mismatched: List<String> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
)

/**
 * "AI link verdict" section: import the optional on-device model, toggle
 * verdicts, remove the bundle. The app itself never downloads — the user
 * side-loads the files from a computer, and every file is hash-checked
 * against the pinned release before it is accepted.
 */
@Composable
private fun LayaSection(
    laya: LayaSettingsUi,
    layaEnabled: Boolean,
    onSetLayaEnabled: (Boolean) -> Unit,
    onImportLayaModel: () -> Unit,
    onDeleteLayaModel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("AI link verdict (on-device)", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Optional: a small decision model judges inspected links for phishing, scam, and malware patterns — " +
                "entirely on this phone. LinkSentry never fetches or sends anything.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (laya.install) {
            LayaInstallUi.INSTALLED -> {
                Text(
                    "Model installed (${laya.totalBytes / (1024L * 1024L)} MB).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Show AI verdicts", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = layaEnabled, onCheckedChange = onSetLayaEnabled)
                }
                TextButton(onClick = onDeleteLayaModel, enabled = !laya.busy) {
                    Text("Remove model")
                }
            }
            LayaInstallUi.CORRUPT -> {
                Text(
                    "The installed bundle does not match the pinned release (${laya.mismatched.joinToString()}). Re-import the files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onImportLayaModel, enabled = !laya.busy) {
                    Text("Select model files")
                }
            }
            LayaInstallUi.NOT_INSTALLED -> {
                Text(
                    "Not installed. The model (~615 MB) is not bundled — LinkSentry cannot download it. " +
                        "On a computer, download all 6 files from " +
                        "huggingface.co/litert-community/Laya-Multilingual-LiteRT (files match the names below), " +
                        "move them to this phone, then select them here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "laya_ml_s256_embeds_wfp16.tflite · laya_ml_act_head_fp32.tflite · " +
                        "laya_ml_calibration.json · tokenizer.json · token_embeddings_fp16.bin · token_embeddings.json",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                TextButton(onClick = onImportLayaModel, enabled = !laya.busy) {
                    Text("Select model files")
                }
            }
        }
        laya.message?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Layout picker button: icon makes the choice obvious at a glance. */
@Composable
private fun LayoutButton(
    label: String,
    description: String,
    icon: @Composable () -> Unit,
    layout: com.dav3.linksentry.domain.model.HandlerLayout,
    settings: AppSettings,
    onSetHandlerLayout: (com.dav3.linksentry.domain.model.HandlerLayout) -> Unit,
) {
    val selected = settings.handlerLayout == layout
    OutlinedButton(
        onClick = { onSetHandlerLayout(layout) },
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                androidx.compose.ui.graphics.Color.Transparent
            },
        ),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        },
    ) {
        icon()
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label)
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
