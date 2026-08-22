package com.dav3.linksentry.ui.inspect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dav3.linksentry.domain.model.HandlerApp
import com.dav3.linksentry.domain.model.Severity
import com.dav3.linksentry.domain.model.Severity.DANGER
import com.dav3.linksentry.domain.model.Severity.INFO
import com.dav3.linksentry.domain.model.Severity.WARN

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
    onOpenBrowserSettings: () -> Unit = {},
    onInspectNew: () -> Unit = {},
    handlerLayout: com.dav3.linksentry.domain.model.HandlerLayout = com.dav3.linksentry.domain.model.HandlerLayout.LIST,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is InspectUiState.Manual -> ManualContent(state, onManualInput, onSubmitManual, onOpenBrowserSettings, modifier)
        is InspectUiState.Inspect -> InspectedContent(
            state,
            onOpenApp,
            onCopy,
            onCopyCleaned,
            onShare,
            onReinspect,
            onInspectNew,
            handlerLayout,
            modifier,
        )
        is InspectUiState.Invalid -> InvalidContent(state, onReinspect, modifier)
    }
}

@Composable
private fun ManualContent(
    state: InspectUiState.Manual,
    onManualInput: (String) -> Unit,
    onSubmitManual: () -> Unit,
    onOpenBrowserSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
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
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            OutlinedButton(onClick = onInspectNew, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Inspect another link")
            }
        }
        item { HostHeader(state) }
        item { VerdictCard(state) }
        if (state.verdict.signals.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("What we noticed", style = MaterialTheme.typography.titleMedium)
                    state.verdict.signals.forEach { signal ->
                        SignalRow(signal.severity, signal.display())
                    }
                }
            }
        }
        item { BreakdownCard(state) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCopy) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
                OutlinedButton(onClick = onCopyCleaned) {
                    Text("Copy cleaned")
                }
                OutlinedButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = null, Modifier.size(16.dp))
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Open with", style = MaterialTheme.typography.titleMedium)
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
            }
        }
        item {
            androidx.compose.material3.TextButton(onClick = onReinspect) {
                Text("Inspect another link")
            }
        }
    }
}

@Composable
private fun HostHeader(state: InspectUiState.Inspect) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            state.facts.host.ifEmpty { state.facts.raw },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            state.facts.raw,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            shape = androidx.compose.foundation.shape.CircleShape,
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
            BreakdownRow("Scheme", state.facts.scheme)
            state.facts.userInfo?.let { BreakdownRow("Credentials", it) }
            BreakdownRow("Host", state.facts.rawHost.ifEmpty { "—" })
            if (state.facts.port != -1) BreakdownRow("Port", state.facts.port.toString())
            BreakdownRow("Path", state.facts.path.ifEmpty { "/" })
            state.facts.params.forEach { p ->
                BreakdownRow(
                    "Param: ${p.name}",
                    p.decodedValue ?: p.rawValue ?: "(no value)",
                )
            }
            state.facts.fragment?.let { BreakdownRow("Fragment", it) }
        }
    }
}

@Composable
private fun BreakdownRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
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
        AppIcon(app, size = 48.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HandlerGrid(apps: List<HandlerApp>, onOpenApp: (HandlerApp) -> Unit) {
    // Fixed 4-column launcher grid.
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        apps.forEach { app ->
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                HandlerGridCell(app, onClick = { onOpenApp(app) })
            }
        }
    }
}

/** App icon with a safe placeholder when the drawable is missing. */
@Composable
private fun AppIcon(app: HandlerApp, size: androidx.compose.ui.unit.Dp = 28.dp) {
    if (app.icon != null) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx -> android.widget.ImageView(ctx).apply { layoutParams = android.view.ViewGroup.LayoutParams(28, 28) } },
            update = { it.setImageDrawable(app.icon) },
            modifier = Modifier.size(size),
        )
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.size(size),
        ) {
            Icon(
                Icons.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier
                    .padding(6.dp)
                    .size(size / 2),
            )
        }
    }
}

@Composable
private fun InvalidContent(state: InspectUiState.Invalid, onReinspect: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
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
