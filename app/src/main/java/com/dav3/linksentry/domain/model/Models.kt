package com.dav3.linksentry.domain.model

/** Ordered least → most severe. */
enum class Severity {
    INFO,
    WARN,
    DANGER,
}

/** Stable identifiers for every risk signal LinkSentry can raise. */
enum class SignalId {
    CREDENTIALS_IN_URL,
    IP_LITERAL_HOST,
    NONSTANDARD_PORT,
    EXPLICIT_DEFAULT_PORT,
    PUNYCODE_HOST,
    MIXED_CASE_HOST,
    SHORTENER_HOST,
    TRACKING_PARAMS,
    DANGEROUS_SCHEME,
    VERY_LONG_URL,
    DEEP_PATH,
    DOTLESS_HOST,
}

data class RiskSignal(
    val id: SignalId,
    val severity: Severity,
)

data class UrlParam(
    val name: String,
    val rawValue: String?,
    val decodedValue: String?,
)

data class UrlFacts(
    val raw: String,
    val scheme: String,
    val userInfo: String?,
    val host: String,
    val rawHost: String,
    val port: Int,
    val effectivePort: Int,
    val path: String,
    val params: List<UrlParam>,
    val fragment: String?,
    val isIpLiteral: Boolean,
    val hasParseError: Boolean,
)

data class Verdict(
    val signals: List<RiskSignal>,
) {
    val worst: Severity?
        get() = signals.maxByOrNull { it.severity }?.severity

    fun has(id: SignalId): Boolean = signals.any { it.id == id }

    fun severityOf(id: SignalId): Severity? = signals.firstOrNull { it.id == id }?.severity
}

data class Analysis(
    val facts: UrlFacts,
    val verdict: Verdict,
)

/** An installed app that can open a given URL. */
data class HandlerApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    /** True if this app is a general web browser (wildcard-host handler). */
    val isBrowser: Boolean,
    /** App icon as an Android [Drawable] (may be adaptive); null on failure. */
    val icon: android.graphics.drawable.Drawable? = null,
)

/** What the user did with an inspected link (history record). */
enum class HistoryAction {
    OPENED_WITH,
    COPIED,
    COPIED_CLEANED,
    SHARED,
    INSPECTED,
}

data class LinkRecord(
    val id: Long = 0,
    val url: String,
    val host: String,
    val worstSeverity: Severity?,
    val action: HistoryAction,
    val timestamp: Long,
    /** Times opened/acted-on (deduped by exact URL). */
    val openCount: Int = 1,
    /** Most recent handler app (direct re-open from history). */
    val lastAppPackage: String? = null,
    val lastAppActivity: String? = null,
    val lastAppLabel: String? = null,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** How the "Open with" handler picker is laid out. */
enum class HandlerLayout {
    LIST,
    GRID,
}

/** What a cleanup step targets. Drives coloring in the UI. */
enum class CleanupCategory {
    /** user:pass@ embedded in the URL */
    CREDENTIALS,

    /** utm_* / fbclid / … tracking parameters */
    TRACKING_PARAM,
}

/** Default treatment of a query param (single source of truth). */
enum class ParamBehavior {
    /** Built-in known tracker (utm_*, fbclid, …) — removed by default. */
    REMOVE,

    /** Unknown param — kept by default. */
    KEEP,

    /** User-taught "always remove" — treated like a built-in tracker. */
    ALWAYS_REMOVE,
}

/** One thing the cleaner would strip from the URL. */
data class UrlRemoval(
    val category: CleanupCategory,
    /** Short token: param name, or the userinfo string. */
    val token: String,
    /** Human explanation. */
    val detail: String,
)

/** Result of cleaning a URL: the cleaned string + everything that was removed. */
data class LinkCleanup(
    val url: String,
    val removals: List<UrlRemoval>,
)

/** Package names of pseudo entries in the handler list (copy/share actions). */
object PseudoHandler {
    /** Impossible as a real package name ('@' is invalid in package names). */
    const val COPY = "@copy"
    const val COPY_CLEANED = "@copy-cleaned"
    const val SHARE = "@share"
}

/** Identity of a danger-override the user granted (never re-warn). */
sealed interface DangerOverride {
    /** Green light for one specific host. */
    data class Host(val host: String) : DangerOverride

    /** Green light for links sharing the same DANGER signals. */
    data class Signals(val ids: Set<com.dav3.linksentry.domain.model.SignalId>) : DangerOverride

    /** Stable storage key ("danger:host:..." / "danger:signals:..."). */
    fun key(): String = when (this) {
        is Host -> "danger:host:$host"
        is Signals -> "danger:signals:" + ids.sorted().joinToString(",") { it.name }
    }
}

data class AppSettings(
    /** Handlers open the cleaned URL when enabled. */
    val openCleaned: Boolean = false,
    /** Param names the user marked as "always remove" (custom trackers). */
    val customTrackingParams: Set<String> = emptySet(),
    val recordHistory: Boolean = true,
    /** null = keep forever */
    val retentionDays: Int? = 30,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val handlerLayout: HandlerLayout = HandlerLayout.LIST,
)
