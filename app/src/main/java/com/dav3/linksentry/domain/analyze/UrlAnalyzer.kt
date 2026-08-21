package com.dav3.linksentry.domain.analyze

import com.dav3.linksentry.domain.model.Analysis
import com.dav3.linksentry.domain.model.RiskSignal
import com.dav3.linksentry.domain.model.Severity
import com.dav3.linksentry.domain.model.Severity.DANGER
import com.dav3.linksentry.domain.model.Severity.INFO
import com.dav3.linksentry.domain.model.Severity.WARN
import com.dav3.linksentry.domain.model.SignalId
import com.dav3.linksentry.domain.model.SignalId.CREDENTIALS_IN_URL
import com.dav3.linksentry.domain.model.SignalId.DANGEROUS_SCHEME
import com.dav3.linksentry.domain.model.SignalId.DEEP_PATH
import com.dav3.linksentry.domain.model.SignalId.DOTLESS_HOST
import com.dav3.linksentry.domain.model.SignalId.EXPLICIT_DEFAULT_PORT
import com.dav3.linksentry.domain.model.SignalId.IP_LITERAL_HOST
import com.dav3.linksentry.domain.model.SignalId.MIXED_CASE_HOST
import com.dav3.linksentry.domain.model.SignalId.NONSTANDARD_PORT
import com.dav3.linksentry.domain.model.SignalId.PUNYCODE_HOST
import com.dav3.linksentry.domain.model.SignalId.SHORTENER_HOST
import com.dav3.linksentry.domain.model.SignalId.TRACKING_PARAMS
import com.dav3.linksentry.domain.model.SignalId.VERY_LONG_URL
import com.dav3.linksentry.domain.model.UrlFacts
import com.dav3.linksentry.domain.model.UrlParam
import com.dav3.linksentry.domain.model.Verdict
import java.net.URLDecoder

/**
 * Pure-Kotlin URL analysis. No network, no Android dependencies — 100% unit
 * testable. Parses arbitrary user input into [UrlFacts] and derives local
 * risk [RiskSignal]s. NEVER claims a link is safe — absence of signals means
 * nothing was recognized, not that the link is trustworthy.
 */
object UrlAnalyzer {

    private val SCHEME_PREFIX = Regex("^[A-Za-z][A-Za-z0-9+.\\-]*:")

    private val DANGEROUS_SCHEMES = setOf("javascript", "data", "intent", "file", "content")

    private val SHORTENERS = setOf(
        "bit.ly", "t.co", "tinyurl.com", "goo.gl", "is.gd", "ow.ly",
        "buff.ly", "cutt.ly", "rebrand.ly", "tiny.cc", "linktr.ee",
    )

    private val TRACKING_PARAM_NAMES = setOf("fbclid", "gclid", "mc_eid", "mc_cid", "ref", "igshid", "si")

    private const val LONG_URL_THRESHOLD = 120
    private const val DEEP_PATH_SEGMENTS = 6L

    // ---------- parse ----------

    fun parse(input: String): UrlFacts {
        val raw = input.trim()
        var rest = raw
        var scheme = ""

        val schemeMatch = SCHEME_PREFIX.find(rest)
        if (schemeMatch != null) {
            scheme = rest.substringBefore(':').lowercase()
            rest = rest.substring(schemeMatch.value.length)
        } else {
            scheme = "https"
            rest = "//$rest"
        }

        val hasAuthority = rest.startsWith("//")
        if (!hasAuthority) {
            // Opaque URI (javascript:alert(1), data:..., mailto:...) — no host.
            return facts(
                raw = raw, scheme = scheme, userInfo = null, host = "", rawHost = "",
                port = -1, path = rest, query = null, fragment = null, parseError = false,
            )
        }
        rest = rest.substring(2)

        // Split authority from path/query/fragment at the first of / ? #
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val authority = if (authorityEnd == -1) rest else rest.substring(0, authorityEnd)
        val afterAuthority = if (authorityEnd == -1) "" else rest.substring(authorityEnd)

        // Userinfo is everything before the LAST '@' in the authority
        // (browser behavior — everything before it is ignored for routing).
        val at = authority.lastIndexOf('@')
        val userInfo = if (at != -1) authority.substring(0, at) else null
        var hostPort = if (at != -1) authority.substring(at + 1) else authority

        // IPv6 literal: [ ... ] with optional :port after the bracket
        val ipv6 = hostPort.startsWith("[")
        var host = ""
        var portStr: String? = null
        var parseError = false
        if (ipv6) {
            val close = hostPort.indexOf(']')
            if (close == -1) {
                parseError = true
                hostPort = ""
            } else {
                host = hostPort.substring(0, close + 1) // keep brackets
                val tail = hostPort.substring(close + 1)
                if (tail.startsWith(":")) portStr = tail.substring(1)
            }
        } else {
            val colon = hostPort.indexOf(':')
            if (colon == -1) {
                host = hostPort
            } else {
                host = hostPort.substring(0, colon)
                portStr = hostPort.substring(colon + 1)
            }
        }

        var port = -1
        if (portStr != null) {
            port = portStr.toIntOrNull()?.takeIf { it in 1..65535 } ?: run {
                parseError = true
                -1
            }
        }

        val rawHost = host
        val hostLower = host.lowercase()
        // Empty host is only an error for web schemes; file:/// etc. legitimately
        // have no authority. Whitespace in a host is always garbage input.
        if ((host.isEmpty() && (scheme == "http" || scheme == "https")) ||
            hostLower.contains(' ') ||
            hostLower.contains('\t')
        ) {
            parseError = true
        }

        // Path / query / fragment
        var pathQueryFragment = afterAuthority
        val hash = pathQueryFragment.indexOf('#')
        val fragment = if (hash != -1) pathQueryFragment.substring(hash + 1) else null
        if (hash != -1) pathQueryFragment = pathQueryFragment.substring(0, hash)
        val q = pathQueryFragment.indexOf('?')
        val query = if (q != -1) pathQueryFragment.substring(q + 1) else null
        val path = if (q != -1) pathQueryFragment.substring(0, q) else pathQueryFragment

        return facts(
            raw = raw, scheme = scheme, userInfo = userInfo, host = hostLower, rawHost = rawHost,
            port = port, path = path, query = query, fragment = fragment, parseError = parseError,
        )
    }

    private fun facts(
        raw: String,
        scheme: String,
        userInfo: String?,
        host: String,
        rawHost: String,
        port: Int,
        path: String,
        query: String?,
        fragment: String?,
        parseError: Boolean,
    ): UrlFacts {
        val isIpLiteral = isIpLiteral(host)
        return UrlFacts(
            raw = raw,
            scheme = scheme,
            userInfo = userInfo,
            host = host,
            rawHost = rawHost,
            port = port,
            effectivePort = port.takeIf { it != -1 } ?: defaultPort(scheme),
            path = path,
            params = parseParams(query),
            fragment = fragment,
            isIpLiteral = isIpLiteral,
            hasParseError = parseError,
        )
    }

    private fun parseParams(query: String?): List<UrlParam> {
        if (query.isNullOrEmpty()) return emptyList()
        return query.split('&').filter { it.isNotEmpty() }.map { pair ->
            val eq = pair.indexOf('=')
            if (eq == -1) {
                UrlParam(name = pair, rawValue = null, decodedValue = null)
            } else {
                val name = pair.substring(0, eq)
                val value = pair.substring(eq + 1)
                UrlParam(name = name, rawValue = value, decodedValue = decode(value))
            }
        }
    }

    private fun decode(value: String): String? = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (_: IllegalArgumentException) {
        value // broken escapes — show raw
    }

    private fun defaultPort(scheme: String): Int = when (scheme) {
        "http" -> 80
        "https" -> 443
        else -> -1
    }

    private fun isIpLiteral(host: String): Boolean {
        if (host.startsWith("[")) return true // IPv6
        val quads = host.split('.')
        if (quads.size != 4) return false
        return quads.all { q -> q.isNotEmpty() && q.all { it.isDigit() } && q.toInt() in 0..255 }
    }

    // ---------- analyze ----------

    fun analyze(input: String): Analysis {
        val f = parse(input)
        return Analysis(facts = f, verdict = Verdict(signals = signals(f)))
    }

    private fun signals(f: UrlFacts): List<RiskSignal> {
        if (f.hasParseError) return emptyList()
        val out = mutableListOf<RiskSignal>()
        fun add(id: SignalId, severity: Severity) {
            out.add(RiskSignal(id, severity))
        }

        if (f.scheme in DANGEROUS_SCHEMES) add(DANGEROUS_SCHEME, DANGER)
        if (f.userInfo != null) add(CREDENTIALS_IN_URL, DANGER)
        if (f.isIpLiteral) add(IP_LITERAL_HOST, WARN)
        if (f.host.split('.').any { it.startsWith("xn--") }) add(PUNYCODE_HOST, DANGER)
        if (f.rawHost.isNotEmpty() && f.rawHost != f.rawHost.lowercase()) add(MIXED_CASE_HOST, INFO)
        if (f.port != -1) {
            val def = defaultPort(f.scheme)
            if (def != -1 && f.port == def) add(EXPLICIT_DEFAULT_PORT, INFO) else add(NONSTANDARD_PORT, WARN)
        }
        if (isShortener(f.host)) add(SHORTENER_HOST, WARN)
        if (f.params.any { isTracking(it.name) }) add(TRACKING_PARAMS, INFO)
        if (f.raw.length > LONG_URL_THRESHOLD) add(VERY_LONG_URL, INFO)
        if (f.path.split('/').count { it.isNotEmpty() } > DEEP_PATH_SEGMENTS) add(DEEP_PATH, INFO)
        if (f.host.isNotEmpty() && !f.isIpLiteral && !f.host.contains('.')) add(DOTLESS_HOST, WARN)

        return out
    }

    private fun isShortener(host: String): Boolean = SHORTENERS.any { host == it || host.endsWith(".$it") }

    private fun isTracking(name: String): Boolean {
        val n = name.lowercase()
        return n in TRACKING_PARAM_NAMES || n.startsWith("utm_")
    }

    // ---------- cleaned URL ----------

    /** URL with credentials stripped and tracking params removed. */
    fun cleanedUrl(f: UrlFacts): String {
        if (f.hasParseError) return f.raw
        val portPart = if (f.port != -1) ":${f.port}" else ""
        val kept = f.params.filterNot { isTracking(it.name) }
        val queryPart = if (kept.isEmpty()) {
            ""
        } else {
            "?" + kept.joinToString("&") { p ->
                if (p.rawValue == null) p.name else "${p.name}=${p.rawValue}"
            }
        }
        val fragmentPart = if (f.fragment != null) "#${f.fragment}" else ""
        return "${f.scheme}://${f.host}$portPart${f.path}$queryPart$fragmentPart"
    }
}
