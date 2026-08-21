package com.dav3.linksentry.ui.inspect

import androidx.compose.ui.graphics.Color
import com.dav3.linksentry.domain.model.RiskSignal
import com.dav3.linksentry.domain.model.Severity
import com.dav3.linksentry.domain.model.SignalId

/** Human-readable copy for every risk signal. Single source for UI text. */
data class SignalCopy(val title: String, val detail: String)

fun SignalId.copy(): SignalCopy = when (this) {
    SignalId.CREDENTIALS_IN_URL -> SignalCopy(
        title = "Credentials embedded in URL",
        detail = "Everything before the @ is ignored for routing — the REAL destination is what follows it. Attackers use this to disguise the true host.",
    )
    SignalId.IP_LITERAL_HOST -> SignalCopy(
        title = "Raw IP address instead of a domain",
        detail = "Legitimate sites almost always use domain names. An IP host is common in phishing and malware links.",
    )
    SignalId.NONSTANDARD_PORT -> SignalCopy(
        title = "Unusual port number",
        detail = "This URL connects on a non-standard port. Normal websites use 80 (http) or 443 (https).",
    )
    SignalId.EXPLICIT_DEFAULT_PORT -> SignalCopy(
        title = "Explicit default port",
        detail = "The port is spelled out but matches the scheme default. Harmless, just unusual.",
    )
    SignalId.PUNYCODE_HOST -> SignalCopy(
        title = "Punycode host — possible lookalike domain",
        detail = "The host contains xn-- labels, used for international characters. This can produce lookalike domains like аpple.com vs apple.com. Verify the site identity carefully.",
    )
    SignalId.MIXED_CASE_HOST -> SignalCopy(
        title = "Mixed-case host",
        detail = "Hosts are case-insensitive; mixed case is sometimes used to hide lookalike domains (e.g. PayPa1.com).",
    )
    SignalId.SHORTENER_HOST -> SignalCopy(
        title = "Shortened link — destination hidden",
        detail = "Shorteners hide where you will actually land. You cannot verify the destination before opening it.",
    )
    SignalId.TRACKING_PARAMS -> SignalCopy(
        title = "Tracking parameters present",
        detail = "This URL contains known tracking parameters. Use \"Copy cleaned\" to get the same link without trackers.",
    )
    SignalId.DANGEROUS_SCHEME -> SignalCopy(
        title = "Dangerous URL scheme",
        detail = "javascript:, data:, intent: and file: URLs can execute code or access local content. Never open these from untrusted sources.",
    )
    SignalId.VERY_LONG_URL -> SignalCopy(
        title = "Unusually long URL",
        detail = "Very long URLs are sometimes used to push the real destination off-screen or hide parameters.",
    )
    SignalId.DEEP_PATH -> SignalCopy(
        title = "Deeply nested path",
        detail = "Many path segments — occasionally used to disguise redirects or payloads.",
    )
    SignalId.DOTLESS_HOST -> SignalCopy(
        title = "Host has no dot",
        detail = "Web addresses normally have at least one dot. Dotless hosts resolve only on some intranets.",
    )
}

fun Severity.color(): Color = when (this) {
    Severity.DANGER -> Color(0xFFDC2626)
    Severity.WARN -> Color(0xFFD97706)
    Severity.INFO -> Color(0xFF2563EB)
}

fun RiskSignal.display(): SignalCopy = id.copy()
