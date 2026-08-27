package com.dav3.linksentry.ui.preview

import com.dav3.linksentry.domain.model.AppSettings
import com.dav3.linksentry.domain.model.HandlerApp
import com.dav3.linksentry.domain.model.HistoryAction
import com.dav3.linksentry.domain.model.LinkRecord
import com.dav3.linksentry.domain.model.RiskSignal
import com.dav3.linksentry.domain.model.Severity
import com.dav3.linksentry.domain.model.SignalId
import com.dav3.linksentry.domain.model.ThemeMode
import com.dav3.linksentry.domain.model.UrlFacts
import com.dav3.linksentry.domain.model.UrlParam
import com.dav3.linksentry.domain.model.Verdict
import com.dav3.linksentry.domain.repository.HistoryRepository
import com.dav3.linksentry.domain.repository.SettingsRepository
import com.dav3.linksentry.domain.system.BrowserRoleChecker
import com.dav3.linksentry.domain.system.LinkActions
import com.dav3.linksentry.ui.history.HistoryViewModel
import com.dav3.linksentry.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fakes + demo data for @Preview rendering (also exercised by the
 * Roborazzi preview-scanner screenshot tests).
 */

class FakeHistoryRepo(records: List<LinkRecord> = emptyList()) : HistoryRepository {
    private val flow = MutableStateFlow(records)

    override fun observeAll(): Flow<List<LinkRecord>> = flow

    override suspend fun record(
        url: String,
        host: String,
        severity: Severity?,
        action: HistoryAction,
    ) = Unit

    override suspend fun clear() = Unit

    override suspend fun delete(id: Long) = Unit

    override suspend fun deleteByUrl(url: String): Int = 0

    override fun search(query: String) = flow
    override suspend fun deleteFound(query: String) = 0
    override suspend fun recordApp(url: String, pkg: String, activity: String?, label: String?) = Unit
}

class FakeSettingsRepo(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val flow = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = flow

    override suspend fun setRecordHistory(enabled: Boolean) = Unit

    override suspend fun setRetentionDays(days: Int?) = Unit

    override suspend fun setTheme(mode: com.dav3.linksentry.domain.model.ThemeMode) = Unit

    override suspend fun setHandlerLayout(layout: com.dav3.linksentry.domain.model.HandlerLayout) = Unit

    override suspend fun setOpenCleaned(enabled: Boolean) = Unit

    override suspend fun setAutoCloseOnOpen(enabled: Boolean) = Unit

    override suspend fun addCustomTrackingParam(name: String) = Unit

    override suspend fun removeCustomTrackingParam(name: String) = Unit

    override suspend fun excludeUrlFromHistory(url: String) = Unit

    override suspend fun unexcludeUrlFromHistory(url: String) = Unit
}

class FakeRoleChecker(private val isDefault: Boolean = true) : BrowserRoleChecker {
    override fun isDefaultBrowser(): Boolean = isDefault
}

object NoopLinkActions : LinkActions {
    override fun openWith(app: HandlerApp, url: String) = true

    override fun copy(text: String) = Unit

    override fun share(url: String) = Unit

    override fun openDefaultAppsSettings() = Unit
}

fun previewHistoryViewModel(records: List<LinkRecord>): HistoryViewModel = HistoryViewModel(FakeHistoryRepo(records), NoopLinkActions, FakeDangerOverrides(), FakeDemoRepo())

fun previewSettingsViewModel(settings: AppSettings): SettingsViewModel = SettingsViewModel(FakeSettingsRepo(settings), FakeRoleChecker(true), NoopLinkActions, FakeHandlerPrefs(), FakeDangerOverrides(), FakeDemoRepo())

class FakeDangerOverrides : com.dav3.linksentry.domain.repository.DangerOverridesRepository {
    override fun observeAll() = kotlinx.coroutines.flow.MutableStateFlow(
        listOf<com.dav3.linksentry.domain.model.DangerOverride>(
            com.dav3.linksentry.domain.model.DangerOverride.Host("trusted.example.com"),
        ),
    )

    override suspend fun all(): List<com.dav3.linksentry.domain.model.DangerOverride> = emptyList()

    override suspend fun grant(override: com.dav3.linksentry.domain.model.DangerOverride) = Unit

    override suspend fun clearAll() = Unit

    override suspend fun revoke(override: com.dav3.linksentry.domain.model.DangerOverride) = Unit
}

class FakeHandlerPrefs : com.dav3.linksentry.domain.repository.HandlerPrefsRepository {
    override fun observeAll() = kotlinx.coroutines.flow.MutableStateFlow(
        emptyList<com.dav3.linksentry.domain.system.HandlerUsage>(),
    )

    override suspend fun forKey(key: String) = emptyList<com.dav3.linksentry.domain.system.HandlerUsage>()

    override suspend fun recordUse(key: String, pkg: String) = Unit

    override suspend fun forget(key: String, pkg: String) = Unit

    override suspend fun clearAll() = Unit

    override suspend fun clearKey(key: String) = Unit
}

val demoFactsDangerous = UrlFacts(
    raw = "http://paypal.com.secure-login@203.0.113.7:8080/session/verify?utm_source=mail&fbclid=abc123",
    scheme = "http",
    userInfo = "paypal.com.secure-login",
    host = "203.0.113.7",
    rawHost = "203.0.113.7",
    port = 8080,
    effectivePort = 8080,
    path = "/session/verify",
    params = listOf(
        UrlParam("utm_source", "mail", "mail"),
        UrlParam("fbclid", "abc123", "abc123"),
    ),
    fragment = null,
    isIpLiteral = true,
    hasParseError = false,
)

val demoVerdictDangerous = Verdict(
    signals = listOf(
        RiskSignal(SignalId.CREDENTIALS_IN_URL, Severity.DANGER),
        RiskSignal(SignalId.IP_LITERAL_HOST, Severity.WARN),
        RiskSignal(SignalId.NONSTANDARD_PORT, Severity.WARN),
        RiskSignal(SignalId.TRACKING_PARAMS, Severity.INFO),
    ),
)

val demoFactsClean = UrlFacts(
    raw = "https://en.wikipedia.org/wiki/URL",
    scheme = "https",
    userInfo = null,
    host = "en.wikipedia.org",
    rawHost = "en.wikipedia.org",
    port = -1,
    effectivePort = 443,
    path = "/wiki/URL",
    params = emptyList(),
    fragment = null,
    isIpLiteral = false,
    hasParseError = false,
)

val demoVerdictClean = Verdict(signals = emptyList())

val demoHandlers = listOf(
    HandlerApp(
        packageName = "com.android.chrome",
        activityName = "com.google.android.apps.chrome.Main",
        label = "Chrome",
        isBrowser = true,
    ),
    HandlerApp(
        packageName = "org.mozilla.firefox",
        activityName = "org.mozilla.firefox.App",
        label = "Firefox",
        isBrowser = true,
    ),
    HandlerApp(
        packageName = "com.reddit.app",
        activityName = "com.reddit.app.MainActivity",
        label = "Reddit",
        isBrowser = false,
    ),
)

val demoHistory = listOf(
    LinkRecord(
        id = 3,
        url = "http://paypal.com.secure-login@203.0.113.7:8080/session/verify",
        host = "203.0.113.7",
        worstSeverity = Severity.DANGER,
        action = HistoryAction.OPENED_WITH,
        timestamp = 1_771_400_000_000,
    ),
    LinkRecord(
        id = 2,
        url = "https://bit.ly/3xK9zQp",
        host = "bit.ly",
        worstSeverity = Severity.WARN,
        action = HistoryAction.COPIED_CLEANED,
        timestamp = 1_771_300_000_000,
    ),
    LinkRecord(
        id = 1,
        url = "https://en.wikipedia.org/wiki/URL",
        host = "en.wikipedia.org",
        worstSeverity = null,
        action = HistoryAction.INSPECTED,
        timestamp = 1_771_200_000_000,
    ),
)

class FakeDemoRepo : com.dav3.linksentry.domain.repository.DemoRepository {
    override fun observe(key: com.dav3.linksentry.domain.repository.DemoKey) = kotlinx.coroutines.flow.MutableStateFlow(false)
    override suspend fun isSeen(key: com.dav3.linksentry.domain.repository.DemoKey) = false
    override suspend fun markSeen(key: com.dav3.linksentry.domain.repository.DemoKey) {}
    override suspend fun unmarkSeen(key: com.dav3.linksentry.domain.repository.DemoKey) {}
}
