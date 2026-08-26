package com.dav3.linksentry.ui.inspect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dav3.linksentry.domain.analyze.UrlAnalyzer
import com.dav3.linksentry.domain.model.CleanupCategory
import com.dav3.linksentry.domain.model.DemoLinks
import com.dav3.linksentry.domain.model.DemoTour
import com.dav3.linksentry.domain.model.HandlerApp
import com.dav3.linksentry.domain.model.ParamBehavior
import com.dav3.linksentry.domain.model.PseudoHandler
import com.dav3.linksentry.domain.model.Severity
import com.dav3.linksentry.domain.model.Severity.DANGER
import com.dav3.linksentry.domain.model.Severity.INFO
import com.dav3.linksentry.domain.model.Severity.WARN
import com.dav3.linksentry.domain.model.SignalId
import com.dav3.linksentry.domain.model.UrlParam
import kotlin.math.roundToInt

/**
 * State-driven Inspect UI — no ViewModel inside, previewable and testable.
 */
@Composable
fun InspectContent(
    state: InspectUiState,
    onOpenApp: (HandlerApp) -> Unit,
    onCopy: () -> Unit,
    onCopyCleaned: () -> Unit,
    onShare: () -> Unit,
    onReinspect: () -> Unit,
    onManualInput: (String) -> Unit,
    onSubmitManual: () -> Unit = {},
    onSubmitDemo: (String) -> Unit = {},
    onOpenBrowserSettings: () -> Unit = {},
    onInspectNew: () -> Unit = {},
    handlerLayout: com.dav3.linksentry.domain.model.HandlerLayout = com.dav3.linksentry.domain.model.HandlerLayout.LIST,
    onToggleKeepParam: (String) -> Unit = {},
    onToggleKeepCredentials: () -> Unit = {},
    onToggleOpenCleaned: () -> Unit = {},
    onToggleRemoveParam: (String) -> Unit = {},
    onMarkParamAsTracking: (String) -> Unit = {},
    onResetSorting: () -> Unit = {},
    onEditUrl: (String) -> Unit = {},
    onBypassGate: () -> Unit = {},
    onTrustHostForever: () -> Unit = {},
    onConfirmOpen: (Boolean) -> Unit = {},
    onCancelConfirm: () -> Unit = {},
    onRevokeOverride: () -> Unit = {},
    onAdvanceTour: () -> Unit = {},
    onSkipTour: () -> Unit = {},
    onToggleEnforceHttps: () -> Unit = {},
    onToggleHistoryExcluded: () -> Unit = {},
    onOpenAppSearch: () -> Unit = {},
    onCloseAppSearch: () -> Unit = {},
    onAppSearchChange: (String) -> Unit = {},
    statusBarInset: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val resolvedInset = if (statusBarInset > 0.dp) {
        statusBarInset
    } else {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    }
    when (state) {
        is InspectUiState.Manual -> ManualContent(state, onManualInput, onSubmitManual, onSubmitDemo, onOpenBrowserSettings, resolvedInset, modifier)
        is InspectUiState.Inspect -> InspectedContent(
            state,
            onOpenApp,
            onCopy,
            onCopyCleaned,
            onShare,
            onReinspect,
            onInspectNew,
            handlerLayout,
            onToggleKeepParam,
            onToggleKeepCredentials,
            onToggleOpenCleaned,
            onToggleRemoveParam,
            onMarkParamAsTracking,
            onResetSorting,
            onEditUrl,
            onBypassGate,
            onTrustHostForever,
            onConfirmOpen,
            onCancelConfirm,
            onRevokeOverride,
            onAdvanceTour,
            onSkipTour,
            onToggleEnforceHttps,
            onToggleHistoryExcluded,
            onOpenAppSearch,
            onCloseAppSearch,
            onAppSearchChange,
            modifier,
        )
        is InspectUiState.Invalid -> InvalidContent(state, onReinspect, resolvedInset, modifier)
    }
}

@Composable
private fun ManualContent(
    state: InspectUiState.Manual,
    onManualInput: (String) -> Unit,
    onSubmitManual: () -> Unit,
    onSubmitDemo: (String) -> Unit,
    onOpenBrowserSettings: () -> Unit,
    statusBarInset: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = statusBarInset + 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Paste or type a link to inspect it before opening.",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (!state.isDefaultBrowser) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Make LinkSentry your default browser to inspect every link you tap.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onOpenBrowserSettings) {
                        Text("Set as default browser")
                    }
                }
            }
        }
        androidx.compose.material3.OutlinedTextField(
            value = state.input,
            onValueChange = onManualInput,
            placeholder = { Text("https://…") },
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
        androidx.compose.material3.Button(
            onClick = onSubmitManual,
            enabled = state.input.isNotBlank(),
        ) {
            Text("Inspect link")
        }

        // Demo / "try it": curated sample links run through the REAL
        // analyzer — one tap, no setup, no network (Immich-style demo).
        Text(
            "No link at hand? Try a sample:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DemoLinkChips(onSubmitDemo)
    }
}

@Composable
private fun DemoLinkChips(onSubmitDemo: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        DemoLinks.all.forEach { sample ->
            androidx.compose.material3.SuggestionChip(
                onClick = { onSubmitDemo(sample.url) },
                label = { Text(sample.label) },
            )
        }
    }
}

@Composable
private fun InspectedContent(
    state: InspectUiState.Inspect,
    onOpenApp: (HandlerApp) -> Unit,
    onCopy: () -> Unit,
    onCopyCleaned: () -> Unit,
    onShare: () -> Unit,
    onReinspect: () -> Unit,
    onInspectNew: () -> Unit = {},
    handlerLayout: com.dav3.linksentry.domain.model.HandlerLayout = com.dav3.linksentry.domain.model.HandlerLayout.LIST,
    onToggleKeepParam: (String) -> Unit = {},
    onToggleKeepCredentials: () -> Unit = {},
    onToggleOpenCleaned: () -> Unit = {},
    onToggleRemoveParam: (String) -> Unit = {},
    onMarkParamAsTracking: (String) -> Unit = {},
    onResetSorting: () -> Unit = {},
    onEditUrl: (String) -> Unit = {},
    onBypassGate: () -> Unit = {},
    onTrustHostForever: () -> Unit = {},
    onConfirmOpen: (Boolean) -> Unit = {},
    onCancelConfirm: () -> Unit = {},
    onRevokeOverride: () -> Unit = {},
    onAdvanceTour: () -> Unit = {},
    onSkipTour: () -> Unit = {},
    onToggleEnforceHttps: () -> Unit = {},
    onToggleHistoryExcluded: () -> Unit = {},
    onOpenAppSearch: () -> Unit = {},
    onCloseAppSearch: () -> Unit = {},
    onAppSearchChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tour = state.tour
    // Status-bar inset (single consumer of the outer Scaffold's insets —
    // the inner Scaffold zeroes its own; see InspectScreen).
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val tourTarget = tour?.let { DemoTour.steps[it.index].target }
    // Live bounds (root coords) of each tourable section, keyed by target.
    val sectionBounds = remember { mutableStateMapOf<DemoTour.Target, Rect>() }
    val overlayHostSize = remember { mutableStateOf(Size.Zero) }
    // Auto-scroll the highlighted section into view as steps advance; after
    // the item scroll, fine-scroll again so the anchor's live bounds land in
    // the visible area (the anchor may sit mid-item).
    LaunchedEffect(tourTarget, state.dangerGate) {
        if (tour != null && tourTarget != null) {
            val idx = when (tourTarget) {
                DemoTour.Target.WELCOME,
                DemoTour.Target.HERO,
                DemoTour.Target.BREAKDOWN,
                DemoTour.Target.CLEANUP,
                DemoTour.Target.SWITCH,
                DemoTour.Target.FINISH,
                -> 0
                DemoTour.Target.GATE -> if (state.dangerGate) 1 else 0
                DemoTour.Target.SIGNALS -> if (state.dangerGate) 0 else 1
                DemoTour.Target.APPS -> if (state.dangerGate) 0 else 2
            }
            listState.animateScrollToItem(idx)
        }
    }
    // Hero auto-expand is a per-step NUDGE (handled inside LinkOverviewCard):
    // expand once when the step starts so the anchors exist, then the user's
    // own tap wins for the rest of the step (user request: tapping the
    // breakdown/cleanup section must toggle it during the tour).
    val heroAutoExpandKey = when (tourTarget) {
        DemoTour.Target.BREAKDOWN, DemoTour.Target.CLEANUP -> tour?.index
        else -> null
    }

    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                top = statusBarInset + 8.dp,
                end = 20.dp,
                bottom = 96.dp, // room for the FAB
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { sectionBounds[DemoTour.Target.HERO] = it.boundsInRoot() },
                ) {
                    LinkOverviewCard(
                        state,
                        onEditUrl,
                        onToggleKeepParam,
                        onToggleKeepCredentials,
                        onToggleOpenCleaned,
                        onToggleRemoveParam,
                        onMarkParamAsTracking,
                        onToggleEnforceHttps,
                        onToggleHistoryExcluded,
                        autoExpandKey = heroAutoExpandKey,
                        sectionBounds = sectionBounds,
                    )
                }
            }
            if (state.dangerGate) {
                // Dangerous link: app grid hidden behind explicit
                // confirmation (user: "explicitly prevent the user from
                // reaching the app grid right away").
                item(key = "gate") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { sectionBounds[DemoTour.Target.GATE] = it.boundsInRoot() },
                    ) {
                        DangerGateCard(state, onBypassGate, onTrustHostForever)
                    }
                }
            } else {
                if (state.overrideActive) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "⚠ Previously allowed: you gave this kind of link a green light.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onRevokeOverride) {
                                Text("Revoke")
                            }
                        }
                    }
                }
            }
            if (state.confirmApp != null) {
                item { ConfirmOpenCard(state, onConfirmOpen, onCancelConfirm) }
            }

            if (!state.dangerGate && state.verdict.signals.isNotEmpty()) {
                item(key = "signals") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { sectionBounds[DemoTour.Target.SIGNALS] = it.boundsInRoot() },
                    ) {
                        Text("What we noticed", style = MaterialTheme.typography.titleMedium)
                        state.verdict.signals.forEach { signal ->
                            SignalRow(signal.severity, signal.display())
                        }
                    }
                }
            }
            if (!state.dangerGate) {
                item(key = "apps") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { sectionBounds[DemoTour.Target.APPS] = it.boundsInRoot() },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Open with",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (tour == null && state.allApps.isEmpty()) {
                                TextButton(onClick = onResetSorting) {
                                    Text("Reset app order")
                                }
                            }
                        }
                        // Tour sandbox: the fallback is hidden during the tour.
                        if (tour != null) {
                            if (state.handlers.isEmpty()) {
                                Text(
                                    "No installed app can open this link.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            if (handlerLayout == com.dav3.linksentry.domain.model.HandlerLayout.GRID) {
                                HandlerGrid(state.handlers, onOpenApp)
                            } else {
                                state.handlers.forEach { app -> HandlerRow(app, onClick = { onOpenApp(app) }) }
                            }
                        } else if (state.allApps.isEmpty()) {
                            if (state.handlers.isEmpty()) {
                                Text(
                                    "No installed app can open this link.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            TextButton(onClick = onOpenAppSearch) {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Can't see an app? Search all apps")
                            }
                            if (handlerLayout == com.dav3.linksentry.domain.model.HandlerLayout.GRID) {
                                HandlerGrid(state.handlers, onOpenApp)
                            } else {
                                state.handlers.forEach { app -> HandlerRow(app, onClick = { onOpenApp(app) }) }
                            }
                        } else {
                            // Search mode: the SAME list, filtered across every
                            // launchable app (handlers included).
                            OutlinedTextField(
                                value = state.appSearch,
                                onValueChange = onAppSearchChange,
                                placeholder = { Text("Filter by app or package name") },
                                singleLine = true,
                                trailingIcon = {
                                    TextButton(onClick = onCloseAppSearch) { Text("Close") }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            when {
                                state.appSearch.isBlank() -> Text(
                                    "Type to search ${state.allApps.size} installed apps.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                state.appSearchResults.isEmpty() -> Text(
                                    "No apps match \"${state.appSearch}\".",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                else -> state.appSearchResults.forEach { app ->
                                    HandlerRow(app, onClick = { onOpenApp(app) })
                                }
                            }
                        }
                    }
                }
            }
        }
        if (tour != null) {
            TourOverlay(
                tour = tour,
                sectionBounds = sectionBounds,
                onAdvance = onAdvanceTour,
                onSkip = onSkipTour,
            )
        }
        // Single "add new" affordance — replaces the old top button and
        // bottom text button.
        SmallFloatingActionButton(
            onClick = onInspectNew,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Inspect another link")
        }
    }
}

/** Danger gate: hides the app list until explicitly dismissed. */
@Composable
private fun DangerGateCard(
    state: InspectUiState.Inspect,
    onBypassGate: () -> Unit,
    onTrustHostForever: () -> Unit,
) {
    Surface(color = Color(0x33DC2626), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Dangerous link detected",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFDC2626),
            )
            state.verdict.signals
                .filter { it.severity == Severity.DANGER }
                .forEach { signal ->
                    Text("• " + signal.display().title, style = MaterialTheme.typography.bodySmall)
                }
            Text(
                "You can still open it, but we want you to confirm first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBypassGate) { Text("Show apps anyway") }
                TextButton(onClick = onTrustHostForever) {
                    Text("Trust this site")
                }
            }
        }
    }
}

/** Confirmation step before opening a danger-gated link. */
@Composable
private fun ConfirmOpenCard(
    state: InspectUiState.Inspect,
    onConfirmOpen: (Boolean) -> Unit,
    onCancelConfirm: () -> Unit,
) {
    Surface(color = Color(0x33DC2626), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Open anyway?",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFDC2626),
            )
            Text(
                "You are about to open ${state.confirmApp?.label ?: "an app"} for a link with dangerous patterns.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onConfirmOpen(false) }) { Text("Open") }
                TextButton(onClick = onCancelConfirm) { Text("Cancel") }
            }
            var dontWarn by rememberSaveable { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = dontWarn, onCheckedChange = { dontWarn = it })
                Text(
                    "Don't warn me about links like this",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * Merged hero: "You are about to open" + host + editable URL, expandable into
 * the full URL breakdown (scheme/host/port/path/params/fragment) with
 * per-param remove/keep and "always remove" (user-taught trackers), plus the
 * cleanup controls — one card instead of three sections (user: merge
 * "URL breakdown" + "you are about to open" + "clean this link").
 */
@Composable
private fun LinkOverviewCard(
    state: InspectUiState.Inspect,
    onEditUrl: (String) -> Unit,
    onToggleKeepParam: (String) -> Unit,
    onToggleKeepCredentials: () -> Unit,
    onToggleOpenCleaned: () -> Unit,
    onToggleRemoveParam: (String) -> Unit,
    onMarkParamAsTracking: (String) -> Unit,
    onToggleEnforceHttps: () -> Unit = {},
    onToggleHistoryExcluded: () -> Unit = {},
    autoExpandKey: Int? = null,
    sectionBounds: MutableMap<DemoTour.Target, Rect>? = null,
) {
    var userExpanded by rememberSaveable(state.url) { mutableStateOf(false) }
    // Tour nudge: expand ONCE when the tour step pointing inside this card
    // starts (key change), then leave the toggle to the user — tapping the
    // section collapses it even mid-tour, and the next step re-nudges.
    LaunchedEffect(autoExpandKey) {
        if (autoExpandKey != null) userExpanded = true
    }
    val expanded = userExpanded
    var editing by remember { mutableStateOf(false) }
    var draft by remember(state.url) { mutableStateOf(state.input) }
    val worst = state.verdict.worst
    val verdictColor = when (worst) {
        Severity.DANGER -> Color(0xFFDC2626)
        Severity.WARN -> Color(0xFFD97706)
        Severity.INFO -> Color(0xFF2563EB)
        null -> Color(0xFF22C55E)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // --- collapsed header: pre-heading + host (tap to expand) ---
            Text(
                "You are about to open",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                state.facts.host.ifEmpty { state.facts.raw },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = verdictColor,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            if (editing) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        onEditUrl(draft)
                        editing = false
                    }) { Text("Apply") }
                    TextButton(onClick = {
                        draft = state.input
                        editing = false
                    }) { Text("Cancel") }
                }
            } else {
                Text(
                    state.facts.raw,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = true },
                )
                Text(
                    "tap to edit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            // Brief always-visible cleanup summary (kept from CleanupCard).
            if (state.cleanup.removals.isNotEmpty()) {
                Text(
                    "${state.cleanup.removals.size} removable: " +
                        state.cleanup.removals.joinToString(", ") { it.token },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                onClick = { userExpanded = !userExpanded },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { userExpanded = !userExpanded }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .onGloballyPositioned { sectionBounds?.set(DemoTour.Target.BREAKDOWN, it.boundsInRoot()) },
                ) {
                    Text(
                        if (expanded) "Hide URL breakdown" else "Show URL breakdown",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (expanded) {
                HorizontalDivider()
                // --- full URL breakdown with per-param control ---
                // Risk coloring derives from the ANALYZER's signals, not
                // re-derived heuristics (single source of truth).
                state.facts.userInfo?.let {
                    BreakdownRow("Credentials", it, CleanupCategory.CREDENTIALS)
                }
                BreakdownRow(
                    "Scheme",
                    state.facts.scheme,
                    color = when (state.facts.scheme) {
                        "https" -> PositiveGreen
                        "http" -> WarnAmber
                        else -> null
                    },
                )
                BreakdownRow(
                    "Host",
                    state.facts.rawHost.ifEmpty { "—" },
                    color = when {
                        state.verdict.has(SignalId.IP_LITERAL_HOST) -> DangerRed
                        state.verdict.has(SignalId.SHORTENER_HOST) -> WarnAmber
                        else -> null
                    },
                )
                if (state.facts.port != -1) {
                    BreakdownRow(
                        "Port",
                        state.facts.port.toString(),
                        color = when {
                            state.verdict.has(SignalId.NONSTANDARD_PORT) -> WarnAmber
                            else -> null
                        },
                    )
                }
                BreakdownRow("Path", state.facts.path.ifEmpty { "/" })
                state.facts.params.forEach { p ->
                    BreakdownParamRow(
                        state = state,
                        param = p,
                        onToggleRemoveParam = onToggleRemoveParam,
                        onMarkTracking = onMarkParamAsTracking,
                    )
                }
                state.facts.fragment?.let { BreakdownRow("Fragment", it) }
            }
            if (expanded && state.cleanup.removals.isNotEmpty()) {
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { sectionBounds?.set(DemoTour.Target.CLEANUP, it.boundsInRoot()) },
                ) {
                    Text("Clean this link", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "These parts can be removed before you open or copy the link:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Credentials stay here (not a query param): keep/remove.
                    state.facts.userInfo?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = CleanupCategory.CREDENTIALS.color(),
                                shape = CircleShape,
                                modifier = Modifier.size(10.dp),
                            ) {}
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    it,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (state.keepCredentials) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        CleanupCategory.CREDENTIALS.color()
                                    },
                                )
                                Text(
                                    "Credentials embedded in the link",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = onToggleKeepCredentials) {
                                Text(if (state.keepCredentials) "Remove" else "Keep")
                            }
                        }
                    }
                    Text("Cleaned link", style = MaterialTheme.typography.labelMedium)
                    Text(
                        state.cleanup.url,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            LabeledToggleRow(
                label = "Open cleaned link",
                supporting = "Handlers below open the cleaned URL while enabled",
                checked = state.openCleaned,
                onCheckedChange = { onToggleOpenCleaned() },
                sectionBounds = sectionBounds,
                tourTarget = DemoTour.Target.SWITCH,
            )
            // Enforce HTTPS (user idea): colorized scheme makes http visible;
            // the checkbox upgrades the URL handlers open.
            if (state.facts.scheme == "http") {
                LabeledToggleRow(
                    label = "Enforce HTTPS",
                    supporting = "Upgrade this link to https before opening",
                    checked = state.enforceHttps,
                    onCheckedChange = { onToggleEnforceHttps() },
                )
            }
            // Per-link history opt-out (user: "an option to not store in
            // history a specific link"). Demo links are always off and say so.
            LabeledToggleRow(
                label = "Keep out of history",
                supporting = if (DemoLinks.isDemo(state.url)) {
                    "Demo links are never recorded"
                } else {
                    "This exact link is not saved to history"
                },
                checked = state.historyExcluded || DemoLinks.isDemo(state.url),
                enabled = !DemoLinks.isDemo(state.url),
                onCheckedChange = { onToggleHistoryExcluded() },
            )
            // --- final URL that will actually open, AFTER the open-cleaned
            //     switch (user request), reflecting its state ---
            HorizontalDivider()
            val willOpen = when {
                state.openCleaned && state.enforceHttps -> "Will open (cleaned, https)"
                state.openCleaned -> "Will open (cleaned)"
                state.enforceHttps -> "Will open (https)"
                else -> "Will open"
            }
            Text(
                willOpen,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                when {
                    state.openCleaned && state.enforceHttps ->
                        UrlAnalyzer.upgradeScheme(state.facts).url
                    state.openCleaned -> state.cleanup.url
                    state.enforceHttps -> UrlAnalyzer.upgradeScheme(state.facts).url
                    else -> state.facts.raw
                },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

/** Unified query-param row — single source of truth for both the list and
 *  the cleanup view (user: dedupe, show type/name/value/behavior). */
@Composable
private fun BreakdownParamRow(
    state: InspectUiState.Inspect,
    param: com.dav3.linksentry.domain.model.UrlParam,
    onToggleRemoveParam: (String) -> Unit,
    onMarkTracking: (String) -> Unit,
) {
    val behavior = UrlAnalyzer.paramBehavior(param.name, state.customTracking)
    val markedRemove = UrlAnalyzer.cleanup(
        state.facts,
        state.keepParams,
        state.keepCredentials,
        state.removeParams,
        state.customTracking,
    ).removals.any { it.token == param.name && it.category == CleanupCategory.TRACKING_PARAM } &&
        param.name !in state.keepParams
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (behavior != ParamBehavior.KEEP) {
                Surface(
                    color = CleanupCategory.TRACKING_PARAM.color(),
                    shape = CircleShape,
                    modifier = Modifier.size(8.dp),
                ) {}
                Spacer(Modifier.width(6.dp))
            }
            Text(
                param.name,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = if (markedRemove) CleanupCategory.TRACKING_PARAM.color() else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onToggleRemoveParam(param.name) }) {
                Text(if (markedRemove) "Keep" else "Remove")
            }
        }
        Text(
            "= ${param.decodedValue ?: param.rawValue ?: "(no value)"} · " +
                when (behavior) {
                    ParamBehavior.ALWAYS_REMOVE -> "always removed (you taught this)"
                    ParamBehavior.REMOVE -> "removed by default"
                    ParamBehavior.KEEP -> "kept by default"
                },
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = if (behavior != ParamBehavior.KEEP) 14.dp else 0.dp),
        )
        if (behavior == ParamBehavior.KEEP) {
            TextButton(onClick = { onMarkTracking(param.name) }) {
                Text("Always remove \"${param.name}\"", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun HostHeader(state: InspectUiState.Inspect, onEditUrl: (String) -> Unit) {
    val worst = state.verdict.worst
    val verdictColor = when (worst) {
        Severity.DANGER -> Color(0xFFDC2626)
        Severity.WARN -> Color(0xFFD97706)
        Severity.INFO -> Color(0xFF2563EB)
        null -> Color(0xFF22C55E)
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "You are about to open",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            state.facts.host.ifEmpty { state.facts.raw },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = verdictColor,
        )
        // Inline multi-line URL editor: most of the link is visible and
        // editable in place (user: "still no multiline input where the url
        // should be displayed to allow user to customize the url").
        var editing by remember { mutableStateOf(false) }
        var draft by remember(state.url) { mutableStateOf(state.input) }
        if (editing) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        onEditUrl(draft)
                        editing = false
                    }) { Text("Apply") }
                    TextButton(onClick = {
                        draft = state.input
                        editing = false
                    }) { Text("Cancel") }
                }
            }
        } else {
            Text(
                state.facts.raw,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { editing = true },
            )
            Text(
                "tap to edit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** Category color used consistently: cleanup card, breakdown rows, buttons. */
@Composable
private fun CleanupCategory.color(): Color = when (this) {
    CleanupCategory.CREDENTIALS -> Color(0xFFDC2626)
    CleanupCategory.TRACKING_PARAM -> Color(0xFF2563EB)
}

/** Breakdown risk-color palette (scheme/host/port rows). */
private val DangerRed = Color(0xFFDC2626)
private val WarnAmber = Color(0xFFD97706)
private val PositiveGreen = Color(0xFF16A34A)

/**
 * "Clean this link": every removal with its category color, per-removal
 * keep/opt-out, the live cleaned URL, and the open-cleaned toggle.
 */
@Composable
private fun CleanupCard(
    state: InspectUiState.Inspect,
    onToggleKeepParam: (String) -> Unit,
    onToggleKeepCredentials: () -> Unit,
    onToggleOpenCleaned: () -> Unit,
) {
    var expanded by rememberSaveable(state.cleanup.url) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Collapsed: one-line summary + toggle; expanded: full detail
            // (user: "expandable with a brief summary always visible").
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            ) {
                Text("Clean this link", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${state.cleanup.removals.size} removable · ${if (state.openCleaned) "on" else "off"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            }
            if (!expanded) {
                // Brief always-visible summary line.
                Text(
                    state.cleanup.removals.joinToString(", ") { it.token },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = state.openCleaned, onCheckedChange = { onToggleOpenCleaned() })
                    Spacer(Modifier.width(8.dp))
                    Text("Open cleaned link", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (expanded) {
                Text(
                    "These parts can be removed before you open or copy the link:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.cleanup.removals.forEach { r ->
                    val kept = when (r.category) {
                        CleanupCategory.CREDENTIALS -> state.keepCredentials
                        CleanupCategory.TRACKING_PARAM -> r.token in state.keepParams
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = r.category.color(),
                            shape = CircleShape,
                            modifier = Modifier.size(10.dp),
                        ) {}
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                r.token,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (kept) MaterialTheme.colorScheme.onSurfaceVariant else r.category.color(),
                            )
                            Text(
                                r.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = {
                                when (r.category) {
                                    CleanupCategory.CREDENTIALS -> onToggleKeepCredentials()
                                    CleanupCategory.TRACKING_PARAM -> onToggleKeepParam(r.token)
                                }
                            },
                        ) {
                            // Action label: what tapping will do.
                            Text(if (kept) "Remove" else "Keep")
                        }
                    }
                }
                HorizontalDivider()
                Text("Cleaned link", style = MaterialTheme.typography.labelMedium)
                Text(
                    state.cleanup.url,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = state.openCleaned, onCheckedChange = { onToggleOpenCleaned() })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Open cleaned link", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Handlers below open the cleaned URL while enabled",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } // end expanded
        }
    }
}

private data class VerdictStyle(
    val bg: Color,
    val fg: Color,
    val title: String,
    val body: String,
)

@Composable
private fun VerdictCard(state: InspectUiState.Inspect) {
    val worst = state.verdict.worst
    val style = when (worst) {
        DANGER -> VerdictStyle(
            Color(0x33DC2626),
            Color(0xFFDC2626),
            title = "Dangerous patterns detected",
            body = "This link shows strong signs of deception. Think twice before opening it.",
        )
        WARN -> VerdictStyle(
            Color(0x33D97706),
            Color(0xFFD97706),
            title = "Caution — some red flags",
            body = "Parts of this link are unusual. Make sure you trust where it leads.",
        )
        INFO -> VerdictStyle(
            Color(0x332563EB),
            Color(0xFF2563EB),
            title = "Minor notes",
            body = "Nothing alarming, just a few things worth knowing.",
        )
        null -> VerdictStyle(
            Color(0x3322C55E),
            Color(0xFF22C55E),
            title = "No obvious red flags",
            body = "This does NOT mean the link is safe — it only means nothing looked suspicious to local checks.",
        )
    }
    Surface(color = style.bg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (worst == null) Icons.Filled.Shield else Icons.Filled.Warning,
                contentDescription = null,
                tint = style.fg,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(style.title, style = MaterialTheme.typography.titleMedium, color = style.fg)
                Text(style.body, style = MaterialTheme.typography.bodySmall, color = style.fg)
            }
        }
    }
}

@Composable
private fun SignalRow(severity: Severity, copy: SignalCopy) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            color = severity.color(),
            shape = CircleShape,
            modifier = Modifier
                .padding(top = 6.dp)
                .size(10.dp),
        ) {}
        Spacer(Modifier.width(12.dp))
        Column {
            Text(copy.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                copy.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BreakdownCard(state: InspectUiState.Inspect) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("URL breakdown", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            state.facts.userInfo?.let {
                BreakdownRow("Credentials", it, CleanupCategory.CREDENTIALS)
            }
            BreakdownRow("Scheme", state.facts.scheme)
            BreakdownRow("Host", state.facts.rawHost.ifEmpty { "—" })
            if (state.facts.port != -1) BreakdownRow("Port", state.facts.port.toString())
            BreakdownRow("Path", state.facts.path.ifEmpty { "/" })
            state.facts.params.forEach { p ->
                val category = if (p.name.lowercase().startsWith("utm_") ||
                    p.name.lowercase() in TRACKING_PARAM_NAMES_LOWER
                ) {
                    CleanupCategory.TRACKING_PARAM
                } else {
                    null
                }
                BreakdownRow(
                    "Param: ${p.name}",
                    p.decodedValue ?: p.rawValue ?: "(no value)",
                    category,
                )
            }
            state.facts.fragment?.let { BreakdownRow("Fragment", it) }
        }
    }
}

/** Lowercased tracker names — mirrors UrlAnalyzer's list for UI coloring. */
private val TRACKING_PARAM_NAMES_LOWER = setOf(
    "fbclid", "gclid", "msclkid", "mc_eid", "igshid", "ref", "ref_src",
    "referrer", "source", "yclid", "_hsenc", "_hsmi", "vero_id", "wickedid",
)

/** Shared toggle row: switch + two-line text INLINE (switch vertically
 *  centered beside the text), whole row tappable, identical control width
 *  on every row so the set stays aligned. */
@Composable
private fun LabeledToggleRow(
    label: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    enabled: Boolean = true,
    sectionBounds: MutableMap<DemoTour.Target, Rect>? = null,
    tourTarget: DemoTour.Target? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { bounds ->
                tourTarget?.let { sectionBounds?.set(it, bounds.boundsInRoot()) }
            }
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { onCheckedChange() },
            ),
    ) {
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null, // whole row toggles; keep single semantics
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                supporting,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Built-in tracker names — "Always remove" only offered for unknowns. */
private val ALL_KNOWN_TRACKERS = TRACKING_PARAM_NAMES_LOWER + setOf("utm_source", "utm_medium")

@Composable
private fun BreakdownRow(
    label: String,
    value: String,
    category: CleanupCategory? = null,
    color: Color? = null,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (category != null) {
                Surface(
                    color = category.color(),
                    shape = CircleShape,
                    modifier = Modifier.size(8.dp),
                ) {}
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = color
                ?: if (category != null) category.color() else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun HandlerRow(app: HandlerApp, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (app.isBrowser) {
                AssistChip(onClick = onClick, label = { Text("Browser") })
            }
        }
    }
}

/** Launcher-style grid cell: big icon, small label underneath. */
@Composable
private fun HandlerGridCell(app: HandlerApp, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .fillMaxWidth(),
    ) {
        AppIcon(app, size = 56.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun HandlerGrid(apps: List<HandlerApp>, onOpenApp: (HandlerApp) -> Unit) {
    // Centered, width-optimized grid (user: "grid centered and better
    // optimized based on the screen width"): compute the column count that
    // fits the available width, then center the resulting rows.
    BoxWithConstraints {
        val cellWidth = 80.dp
        val gap = 8.dp
        val columns = ((maxWidth - gap) / (cellWidth + gap)).toInt().coerceAtLeast(1)
        val rows = apps.chunked(columns)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            rows.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    modifier = Modifier, // natural width, centered by parent
                ) {
                    row.forEach { app ->
                        Box(modifier = Modifier.width(cellWidth).height(112.dp)) {
                            HandlerGridCell(app, onClick = { onOpenApp(app) })
                        }
                    }
                }
            }
        }
    }
}

/** App icon with a safe placeholder when the drawable is missing. */
@Composable
private fun AppIcon(app: HandlerApp, size: androidx.compose.ui.unit.Dp = 34.dp) {
    val (icon, bg) = when (app.packageName) {
        PseudoHandler.COPY -> Icons.Filled.ContentCopy to MaterialTheme.colorScheme.secondaryContainer
        PseudoHandler.COPY_CLEANED -> Icons.Filled.CleaningServices to MaterialTheme.colorScheme.secondaryContainer
        PseudoHandler.SHARE -> Icons.Filled.Share to MaterialTheme.colorScheme.secondaryContainer
        else -> null to null
    }
    if (app.icon != null) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx -> android.widget.ImageView(ctx).apply { layoutParams = android.view.ViewGroup.LayoutParams(28, 28) } },
            update = { it.setImageDrawable(app.icon) },
            modifier = Modifier.size(size),
        )
    } else if (icon != null) {
        Surface(
            color = bg ?: MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape,
            modifier = Modifier.size(size),
        ) {
            Icon(
                icon,
                contentDescription = app.label,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .padding(size / 5)
                    .size(size),
            )
        }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape,
            modifier = Modifier.size(size),
        ) {
            Icon(
                Icons.Filled.OpenInNew,
                null,
                modifier = Modifier
                    .padding(6.dp)
                    .size(size / 2),
            )
        }
    }
}

@Composable
private fun InvalidContent(
    state: InspectUiState.Invalid,
    onReinspect: () -> Unit,
    statusBarInset: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 20.dp,
                top = statusBarInset + 20.dp,
                end = 20.dp,
                bottom = 20.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Not a valid URL", style = MaterialTheme.typography.titleLarge)
        Text(
            state.input,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            "This text could not be parsed as a URL. Nothing was opened.",
            style = MaterialTheme.typography.bodyMedium,
        )
        androidx.compose.material3.Button(onClick = onReinspect) {
            Text("Try another link")
        }
    }
}

/**
 * Guided-tour spotlight overlay: a dark scrim with a rounded cut-out hole
 * around the highlighted section (live bounds from [sectionBounds]), and a
 * tooltip anchored directly below/above the hole — never pinned to the top.
 * WELCOME/FINISH steps dim the whole screen with a centered tooltip. The
 * scrim is purely visual; the tour is sandboxed in the ViewModel, so the
 * highlighted section stays interactive while it is up.
 */
@Composable
private fun TourOverlay(
    tour: InspectUiState.TourState,
    sectionBounds: Map<DemoTour.Target, Rect>,
    onAdvance: () -> Unit,
    onSkip: () -> Unit,
) {
    val step = DemoTour.steps[tour.index]
    val total = DemoTour.steps.size
    val ringColor = MaterialTheme.colorScheme.primary
    val scrimColor = Color(0xB3000000)
    var overlayPos by remember { mutableStateOf(Offset.Zero) }
    var hostHeight by remember { mutableStateOf(0) }
    var tooltipHeight by remember { mutableStateOf(0) }

    val anchored = step.target != DemoTour.Target.WELCOME && step.target != DemoTour.Target.FINISH
    // Section anchors report bounds in root coordinates; translate them into
    // this overlay's own space (the overlay fills the content area).
    val hole = sectionBounds[step.target]?.translate(-overlayPos.x, -overlayPos.y)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                overlayPos = it.positionInRoot()
                hostHeight = it.size.height
            },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (anchored && hole != null) {
                val r = hole.inflate(24f) // breathing room around the section
                val path = Path().apply {
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(r, CornerRadius(40f, 40f)),
                    )
                    fillType = PathFillType.EvenOdd
                }
                drawPath(path, scrimColor)
                drawRoundRect(
                    color = ringColor,
                    topLeft = Offset(r.left, r.top),
                    size = Size(r.width, r.height),
                    cornerRadius = CornerRadius(40f, 40f),
                    style = Stroke(width = 6f),
                )
            } else {
                drawRect(scrimColor)
            }
        }

        // Tooltip placement: below the hole when it fits, otherwise above;
        // centered for the unanchored welcome/finish steps.
        val tooltipH = tooltipHeight.toFloat()
        val y = if (!anchored || hole == null) {
            (hostHeight - tooltipH) / 2f
        } else {
            val below = hole.bottom + 40f
            if (below + tooltipH <= hostHeight - 40f) below else hole.top - tooltipH - 40f
        }.coerceIn(24f, (hostHeight - tooltipH - 24f).coerceAtLeast(24f))

        Surface(
            color = MaterialTheme.colorScheme.inverseSurface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .onSizeChanged { tooltipHeight = it.height }
                .absoluteOffset { IntOffset(0, y.roundToInt()) },
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        step.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${tour.index + 1}/$total",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.inversePrimary,
                    )
                }
                Text(
                    step.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
                if (tour.notice != null) {
                    Text(
                        tour.notice,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.inversePrimary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onSkip) {
                        Text("Skip tour", color = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                    Button(onClick = onAdvance) {
                        Text(if (tour.index == total - 1) "Finish" else "Next")
                    }
                }
                LinearProgressIndicator(
                    progress = { (tour.index + 1f) / total },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
