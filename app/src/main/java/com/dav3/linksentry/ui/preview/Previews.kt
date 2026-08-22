package com.dav3.linksentry.ui.preview

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.dav3.linksentry.domain.model.AppSettings
import com.dav3.linksentry.domain.model.ThemeMode
import com.dav3.linksentry.ui.history.HistoryContent
import com.dav3.linksentry.ui.inspect.InspectContent
import com.dav3.linksentry.ui.inspect.InspectUiState
import com.dav3.linksentry.ui.settings.SettingsContent
import com.dav3.linksentry.ui.theme.LinkSentryTheme

/**
 * Screenshot-test surfaces — rendered headlessly by the Roborazzi
 * preview scanner (see app/build.gradle.kts roborazzi block).
 */

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun InspectManualPreview() {
    LinkSentryTheme {
        Surface {
            InspectContent(
                state = InspectUiState.Manual(
                    input = "",
                    isDefaultBrowser = true,
                    clipboardHasUrl = false,
                ),
                onOpenApp = {},
                onCopy = {},
                onCopyCleaned = {},
                onShare = {},
                onReinspect = {},
                onManualInput = {},
                onSubmitManual = {},
                onOpenBrowserSettings = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun InspectDangerousPreview() {
    LinkSentryTheme {
        Surface {
            InspectContent(
                state = InspectUiState.Inspect(
                    url = demoFactsDangerous.raw,
                    facts = demoFactsDangerous,
                    verdict = demoVerdictDangerous,
                    handlers = demoHandlers,
                    cleanedUrl = "http://203.0.113.7:8080/session/verify",
                    cleanup = com.dav3.linksentry.domain.analyze.UrlAnalyzer.cleanup(demoFactsDangerous),
                ),
                onOpenApp = {},
                onCopy = {},
                onCopyCleaned = {},
                onShare = {},
                onReinspect = {},
                onManualInput = {},
                onSubmitManual = {},
                onOpenBrowserSettings = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun InspectCleanPreview() {
    LinkSentryTheme {
        Surface {
            InspectContent(
                state = InspectUiState.Inspect(
                    url = demoFactsClean.raw,
                    facts = demoFactsClean,
                    verdict = demoVerdictClean,
                    handlers = demoHandlers,
                    cleanedUrl = demoFactsClean.raw,
                    cleanup = com.dav3.linksentry.domain.analyze.UrlAnalyzer.cleanup(demoFactsClean),
                ),
                onOpenApp = {},
                onCopy = {},
                onCopyCleaned = {},
                onShare = {},
                onReinspect = {},
                onManualInput = {},
                onSubmitManual = {},
                onOpenBrowserSettings = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun InspectInvalidPreview() {
    LinkSentryTheme {
        Surface {
            InspectContent(
                state = InspectUiState.Invalid(input = "not a url $$ ::"),
                onOpenApp = {},
                onCopy = {},
                onCopyCleaned = {},
                onShare = {},
                onReinspect = {},
                onManualInput = {},
                onSubmitManual = {},
                onOpenBrowserSettings = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun HistoryPreview() {
    LinkSentryTheme {
        Surface {
            HistoryContent(
                records = demoHistory,
                onReinspect = {},
                onClear = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 690)
@Composable
private fun SettingsPreview() {
    LinkSentryTheme {
        Surface {
            SettingsContent(
                settings = AppSettings(),
                isDefaultBrowser = true,
                onSetRecordHistory = {},
                onSetRetention = {},
                onSetTheme = {},
                onOpenDefaultAppsSettings = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 690)
@Composable
private fun SettingsNotDefaultPreview() {
    LinkSentryTheme {
        Surface {
            SettingsContent(
                settings = AppSettings(theme = ThemeMode.DARK),
                isDefaultBrowser = false,
                onSetRecordHistory = {},
                onSetRetention = {},
                onSetTheme = {},
                onOpenDefaultAppsSettings = {},
            )
        }
    }
}
