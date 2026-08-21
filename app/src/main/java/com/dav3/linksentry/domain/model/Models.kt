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
