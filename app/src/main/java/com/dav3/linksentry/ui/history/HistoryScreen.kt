package com.dav3.linksentry.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dav3.linksentry.domain.analyze.UrlAnalyzer
import com.dav3.linksentry.domain.model.DangerOverride
import com.dav3.linksentry.domain.model.HandlerApp
import com.dav3.linksentry.domain.model.HistoryAction
import com.dav3.linksentry.domain.model.LinkRecord
import com.dav3.linksentry.domain.model.Severity
import com.dav3.linksentry.domain.repository.DangerOverridesRepository
import com.dav3.linksentry.domain.repository.HistoryRepository
import com.dav3.linksentry.domain.system.LinkActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: HistoryRepository,
    private val actions: LinkActions,
    private val dangerOverrides: DangerOverridesRepository,
) : ViewModel() {
    val query = MutableStateFlow("")

    private val allRecords = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Records matching the live query (blank = all). */
    val records = combine(query, allRecords) { q, list ->
        if (q.isBlank()) list else list.filter { it.url.contains(q, ignoreCase = true) || it.host.contains(q, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun search(q: String) {
        query.value = q
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun clear() {
        viewModelScope.launch { repository.clear() }
    }

    fun deleteFound() {
        viewModelScope.launch { repository.deleteFound(query.value) }
    }

    /** Repeat the recorded action straight from history. */
    fun repeatAction(record: LinkRecord) {
        when (record.action) {
            HistoryAction.COPIED -> actions.copy(record.url)
            HistoryAction.COPIED_CLEANED -> actions.copy(UrlAnalyzer.cleanedUrl(UrlAnalyzer.analyze(record.url).facts))
            HistoryAction.SHARED -> actions.share(record.url)
            HistoryAction.INSPECTED, HistoryAction.OPENED_WITH -> Unit
        }
    }

    /** Danger-gated launch info per URL: null = blocked or no app recorded. */
    val lastAppLaunch: MutableStateFlow<Map<String, LastAppLaunch>> = MutableStateFlow(emptyMap())

    private suspend fun launchInfo(record: LinkRecord): LastAppLaunch? {
        val pkg = record.lastAppPackage ?: return null
        val activity = record.lastAppActivity ?: return null
        val overrides = dangerOverrides.all()
        val analysis = UrlAnalyzer.analyze(record.url)
        val hostOverride = overrides.any { it is DangerOverride.Host && it.host == analysis.facts.host }
        val dangerIds = analysis.verdict.signals.filter { it.severity == Severity.DANGER }.map { it.id }.toSet()
        val signalsOverride = overrides.any { override ->
            override is DangerOverride.Signals && dangerIds.isNotEmpty() && with(override) { ids == dangerIds }
        }
        val allowed = analysis.verdict.worst != Severity.DANGER || hostOverride || signalsOverride
        if (!allowed) return null
        return LastAppLaunch(pkg, activity, record.lastAppLabel ?: pkg)
    }

    /** Direct re-open in the last handler app; blocked for DANGER links
     *  unless the user whitelisted them (host or signal-set override). */
    fun openWithLastApp(record: LinkRecord) {
        viewModelScope.launch {
            val info = launchInfo(record) ?: return@launch
            actions.openWith(
                HandlerApp(
                    packageName = info.packageName,
                    activityName = info.activityName,
                    label = info.label,
                    isBrowser = true,
                ),
                record.url,
            )
        }
    }

    /** Precompute launchability + icon for the visible rows. */
    fun refreshLastAppLaunches(records: List<LinkRecord>) {
        viewModelScope.launch {
            val entries = mutableListOf<Pair<String, LastAppLaunch>>()
            for (r in records) {
                val info = launchInfo(r) ?: continue
                entries.add(r.url to info)
                if (entries.size >= 50) break // bound the work
            }
            lastAppLaunch.value = entries.toMap()
        }
    }

    data class LastAppLaunch(val packageName: String, val activityName: String, val label: String)
}

@Composable
fun HistoryScreen(
    onReinspect: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val records by viewModel.records.collectAsState()
    val query by viewModel.query.collectAsState()
    val lastAppLaunch by viewModel.lastAppLaunch.collectAsState()
    LaunchedEffect(records) { viewModel.refreshLastAppLaunches(records) }
    HistoryContent(
        lastAppLaunch = lastAppLaunch,
        records = records,
        query = query,
        onQueryChange = viewModel::search,
        onReinspect = onReinspect,
        onClear = viewModel::clear,
        onDelete = viewModel::delete,
        onRepeatAction = viewModel::repeatAction,
        onDeleteFound = viewModel::deleteFound,
        onOpenWithLastApp = viewModel::openWithLastApp,
    )
}

/** State-driven body — no ViewModel, previewable and screenshot-testable. */
@Composable
fun HistoryContent(
    records: List<LinkRecord>,
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    onReinspect: (String) -> Unit,
    onClear: () -> Unit,
    onDelete: (Long) -> Unit = {},
    onRepeatAction: (LinkRecord) -> Unit = {},
    onDeleteFound: () -> Unit = {},
    onOpenWithLastApp: (LinkRecord) -> Unit = {},
    lastAppLaunch: Map<String, HistoryViewModel.LastAppLaunch> = emptyMap(),
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var showDeleteFoundDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "History",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (records.isNotEmpty()) {
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Clear history")
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search links…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )
        if (query.isNotBlank()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${records.size} found",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showDeleteFoundDialog = true }) {
                    Text("Delete all found")
                }
            }
        }
        if (records.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (query.isNotBlank()) "No links match \"$query\"." else "No inspected links yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(records, key = { it.id }) { record ->
                    HistoryRow(
                        record,
                        launchMap = lastAppLaunch,
                        onClick = { onReinspect(record.url) },
                        onDelete = { onDelete(record.id) },
                        onRepeatAction = onRepeatAction,
                        onOpenWithLastApp = onOpenWithLastApp,
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear history?") },
            text = { Text("All inspected-link records will be deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    showClearDialog = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
        )
    }
    if (showDeleteFoundDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteFoundDialog = false },
            title = { Text("Delete all found?") },
            text = { Text("Every record matching \"$query\" will be deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteFound()
                    showDeleteFoundDialog = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteFoundDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun HistoryRow(
    record: LinkRecord,
    launchMap: Map<String, HistoryViewModel.LastAppLaunch>,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    onRepeatAction: (LinkRecord) -> Unit = {},
    onOpenWithLastApp: (LinkRecord) -> Unit = {},
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LastAppIcon(record, launchMap, onOpenWithLastApp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(record.host, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    record.url,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        if (record.openCount > 0) append("opened ${record.openCount}×")
                        if (record.openCount > 1) append(" · ")
                        if (record.openCount > 1 || (record.openCount > 0 && record.action != HistoryAction.INSPECTED)) {
                            append("last opened ${relativeTime(record.timestamp)}")
                        }
                        if (record.action == HistoryAction.INSPECTED && record.openCount == 0) {
                            append("inspected, never opened")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    record.action.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Delete entry",
                        modifier = Modifier.size(16.dp),
                    )
                }
                // Quick repeat of what the user last did (copy/share) — shown
                // for every copy/share record now, INSPECTED/OPENED_WITH rows
                // rely on the row tap (re-inspect) + last-app icon instead.
                if (record.action != HistoryAction.INSPECTED && record.action != HistoryAction.OPENED_WITH) {
                    TextButton(onClick = { onRepeatAction(record) }) {
                        Text(
                            when (record.action) {
                                HistoryAction.COPIED -> "Copy again"
                                HistoryAction.COPIED_CLEANED -> "Copy cleaned"
                                HistoryAction.SHARED -> "Share"
                                else -> ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

/** Last-handler icon: real launcher icon when an app is recorded and
 *  launching is allowed; danger-gated otherwise; host initial fallback. */
@Composable
private fun LastAppIcon(
    record: LinkRecord,
    launchMap: Map<String, HistoryViewModel.LastAppLaunch>,
    onOpenWithLastApp: (LinkRecord) -> Unit,
) {
    val info = launchMap[record.url]
    val dangerous = record.worstSeverity == Severity.DANGER
    if (info != null) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val icon = androidx.compose.runtime.remember(info.packageName) {
            runCatching {
                context.packageManager.getApplicationIcon(info.packageName)
            }.getOrNull()
        }
        Surface(
            onClick = { onOpenWithLastApp(record) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(40.dp),
        ) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx -> android.widget.ImageView(ctx).apply { layoutParams = android.view.ViewGroup.LayoutParams(40, 40) } },
                update = { it.setImageDrawable(icon) },
                modifier = Modifier.size(40.dp),
            )
        }
    } else if (record.lastAppPackage != null) {
        // App recorded but launch currently blocked (danger gate).
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    record.lastAppLabel?.take(1)?.uppercase() ?: "!",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    } else {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    hostInitial(record.host),
                    style = MaterialTheme.typography.titleSmall,
                    color = record.worstSeverity?.colorOrNull() ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun hostInitial(host: String): String = host.removePrefix("www.").take(1).uppercase().ifEmpty { "?" }

private fun Severity.colorOrNull(): androidx.compose.ui.graphics.Color? = when (this) {
    Severity.DANGER -> androidx.compose.ui.graphics.Color(0xFFDC2626)
    Severity.WARN -> androidx.compose.ui.graphics.Color(0xFFD97706)
    Severity.INFO -> androidx.compose.ui.graphics.Color(0xFF2563EB)
}

private fun HistoryAction.label(): String = when (this) {
    HistoryAction.OPENED_WITH -> "Opened"
    HistoryAction.COPIED -> "Copied"
    HistoryAction.COPIED_CLEANED -> "Copied (clean)"
    HistoryAction.SHARED -> "Shared"
    HistoryAction.INSPECTED -> "Inspected"
}

/** Compact relative time: "2m ago", "3h ago", "4d ago", or date. */
private fun relativeTime(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    val m = diff / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "${'$'}{m}m ago"
        m < 60 * 24 -> "${'$'}{m / 60}h ago"
        m < 60 * 24 * 30 -> "${'$'}{m / (60 * 24)}d ago"
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.ROOT).format(java.util.Date(ts))
    }
}
