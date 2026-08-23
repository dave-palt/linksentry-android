package com.dav3.linksentry.domain.analyze

import com.dav3.linksentry.domain.model.CleanupCategory
import com.dav3.linksentry.domain.model.ParamBehavior
import com.dav3.linksentry.domain.model.Severity.DANGER
import com.dav3.linksentry.domain.model.Severity.INFO
import com.dav3.linksentry.domain.model.Severity.WARN
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlAnalyzerTest {

    // ---------- parse: happy path ----------

    @Test
    fun `parses basic https URL`() {
        val f = UrlAnalyzer.parse("https://example.com/a/b?q=1&r=2#frag")
        assertEquals("https", f.scheme)
        assertEquals("example.com", f.host)
        assertEquals(-1, f.port)
        assertEquals("/a/b", f.path)
        assertEquals(listOf("q" to "1", "r" to "2"), f.params.map { it.name to it.rawValue })
        assertEquals("frag", f.fragment)
        assertNull(f.userInfo)
        assertFalse(f.hasParseError)
    }

    @Test
    fun `auto-prefixes https when scheme missing`() {
        val f = UrlAnalyzer.parse("example.com/x")
        assertEquals("https", f.scheme)
        assertEquals("example.com", f.host)
        assertEquals("/x", f.path)
    }

    @Test
    fun `URL with no path`() {
        val f = UrlAnalyzer.parse("https://example.com")
        assertEquals("", f.path)
        assertTrue(f.params.isEmpty())
    }

    // ---------- parse: hostile inputs ----------

    @Test
    fun `extracts userinfo credentials`() {
        val f = UrlAnalyzer.parse("http://user:secret@gitlab.com/x")
        assertEquals("user:secret", f.userInfo)
        assertEquals("gitlab.com", f.host)
    }

    @Test
    fun `ip host and port`() {
        val f = UrlAnalyzer.parse("http://192.168.1.5:8080/x")
        assertEquals("192.168.1.5", f.host)
        assertEquals(8080, f.port)
        assertEquals(8080, f.effectivePort)
        assertTrue(f.isIpLiteral)
    }

    @Test
    fun `ipv6 literal in brackets`() {
        val f = UrlAnalyzer.parse("http://[::1]:9000/x")
        assertTrue(f.isIpLiteral)
        assertEquals(9000, f.port)
    }

    @Test
    fun `mixed case host is lowercased for comparison, raw preserved`() {
        val f = UrlAnalyzer.parse("HTTP://ExAmPlE.CoM/P")
        assertEquals("example.com", f.host)
        assertEquals("ExAmPlE.CoM", f.rawHost)
        assertEquals("http", f.scheme) // scheme normalized lowercase
    }

    @Test
    fun `duplicate params preserved in order`() {
        val f = UrlAnalyzer.parse("https://a.com/p?x=1&y=2&x=3")
        assertEquals(listOf("x", "y", "x"), f.params.map { it.name })
        assertEquals(listOf("1", "2", "3"), f.params.map { it.rawValue })
    }

    @Test
    fun `percent-encoded param decoded for display, raw kept`() {
        val f = UrlAnalyzer.parse("https://a.com/p?q=%2Fpath%20a")
        val p = f.params.single()
        assertEquals("%2Fpath%20a", p.rawValue)
        assertEquals("/path a", p.decodedValue)
    }

    @Test
    fun `valueless param`() {
        val f = UrlAnalyzer.parse("https://a.com/p?flag")
        assertEquals("flag", f.params.single().name)
        assertNull(f.params.single().rawValue)
    }

    @Test
    fun `unparsable garbage sets parseError`() {
        val f = UrlAnalyzer.parse("not a url at all $$ ::")
        assertTrue(f.hasParseError)
        // Garbage input still yields empty verdict — no crash, no signals.
        assertTrue(UrlAnalyzer.analyze("not a url at all $$ ::").verdict.signals.isEmpty())
    }

    // ---------- signals 1-3: credentials, IP host, port ----------

    @Test
    fun `credentials in URL is DANGER`() {
        val v = UrlAnalyzer.analyze("http://google.com@evil.com/").verdict
        assertEquals(DANGER, v.severityOf(CREDENTIALS_IN_URL))
    }

    @Test
    fun `ip literal host is WARN`() {
        val v = UrlAnalyzer.analyze("http://192.168.1.5/x").verdict
        assertEquals(WARN, v.severityOf(IP_LITERAL_HOST))
    }

    @Test
    fun `nonstandard port is WARN`() {
        val v = UrlAnalyzer.analyze("https://a.com:8443/x").verdict
        assertEquals(WARN, v.severityOf(NONSTANDARD_PORT))
    }

    @Test
    fun `explicit default port is INFO only`() {
        val v = UrlAnalyzer.analyze("https://a.com:443/x").verdict
        assertEquals(INFO, v.severityOf(EXPLICIT_DEFAULT_PORT))
        assertNull(v.severityOf(NONSTANDARD_PORT))
    }

    // ---------- signals 4-5: punycode, shorteners ----------

    @Test
    fun `punycode host is DANGER`() {
        val v = UrlAnalyzer.analyze("https://xn--pypal-4ve.com/login").verdict
        assertEquals(DANGER, v.severityOf(PUNYCODE_HOST))
    }

    @Test
    fun `mixed case host is INFO`() {
        val v = UrlAnalyzer.analyze("https://PayPa1.example.com/").verdict
        assertEquals(INFO, v.severityOf(MIXED_CASE_HOST))
    }

    @Test
    fun `known shortener is WARN`() {
        assertEquals(WARN, UrlAnalyzer.analyze("https://bit.ly/3xYz").verdict.severityOf(SHORTENER_HOST))
        assertEquals(WARN, UrlAnalyzer.analyze("https://t.co/abc").verdict.severityOf(SHORTENER_HOST))
        assertEquals(WARN, UrlAnalyzer.analyze("https://tinyurl.com/y5xyz").verdict.severityOf(SHORTENER_HOST))
    }

    @Test
    fun `shortener subdomain matches`() {
        val v = UrlAnalyzer.analyze("https://linktr.ee/somebody").verdict
        assertEquals(WARN, v.severityOf(SHORTENER_HOST))
    }

    @Test
    fun `normal host is not a shortener`() {
        val v = UrlAnalyzer.analyze("https://bit.ly.example.com/").verdict
        assertNull(v.severityOf(SHORTENER_HOST))
    }

    // ---------- signals 6-9 ----------

    @Test
    fun `tracking params are INFO`() {
        val v = UrlAnalyzer.analyze("https://a.com/p?utm_source=x&fbclid=abc&id=2").verdict
        assertEquals(INFO, v.severityOf(TRACKING_PARAMS))
    }

    @Test
    fun `any utm_ prefix param is tracking`() {
        val v = UrlAnalyzer.analyze("https://a.com/p?utm_whatever=1").verdict
        assertEquals(INFO, v.severityOf(TRACKING_PARAMS))
    }

    @Test
    fun `dangerous scheme is DANGER`() {
        for (s in listOf("javascript:alert(1)", "data:text/html;base64,SGk=", "intent://x/#Intent", "file:///etc/hosts")) {
            assertEquals(DANGER, UrlAnalyzer.analyze(s).verdict.severityOf(DANGEROUS_SCHEME))
        }
    }

    @Test
    fun `very long URL is INFO`() {
        val v = UrlAnalyzer.analyze("https://a.com/" + "a".repeat(150)).verdict
        assertEquals(INFO, v.severityOf(VERY_LONG_URL))
    }

    @Test
    fun `deep path nesting is INFO`() {
        val v = UrlAnalyzer.analyze("https://a.com/1/2/3/4/5/6/7/8").verdict
        assertEquals(INFO, v.severityOf(DEEP_PATH))
    }

    @Test
    fun `dotless host is WARN`() {
        val v = UrlAnalyzer.analyze("http://intranet/x").verdict
        assertEquals(WARN, v.severityOf(DOTLESS_HOST))
    }

    // ---------- cleaned URL ----------

    @Test
    fun `cleanedUrl strips credentials and tracking params`() {
        val f = UrlAnalyzer.parse("https://user:pass@a.com/p?utm_source=x&id=2")
        assertEquals("https://a.com/p?id=2", UrlAnalyzer.cleanedUrl(f))
    }

    @Test
    fun `cleanedUrl drops empty query`() {
        val f = UrlAnalyzer.parse("http://a.com/?ref=1")
        assertEquals("http://a.com/", UrlAnalyzer.cleanedUrl(f))
    }

    @Test
    fun `cleanedUrl keeps fragment and port`() {
        val f = UrlAnalyzer.parse("https://a.com:8443/p?x=1&fbclid=1#s")
        assertEquals("https://a.com:8443/p?x=1#s", UrlAnalyzer.cleanedUrl(f))
    }

    @Test
    fun `cleanedUrl on clean URL is identity`() {
        val url = "https://example.com/a/b?q=1"
        assertEquals(url, UrlAnalyzer.cleanedUrl(UrlAnalyzer.parse(url)))
    }

    @Test
    fun `paramBehavior honors custom tracking rules`() {
        assertEquals(ParamBehavior.REMOVE, UrlAnalyzer.paramBehavior("utm_source"))
        assertEquals(ParamBehavior.REMOVE, UrlAnalyzer.paramBehavior("fbclid"))
        assertEquals(ParamBehavior.KEEP, UrlAnalyzer.paramBehavior("trackid"))
        assertEquals(
            ParamBehavior.ALWAYS_REMOVE,
            UrlAnalyzer.paramBehavior("trackid", customTracking = setOf("trackid")),
        )
    }

    @Test
    fun `manual removeParams strips unknown params`() {
        val f = UrlAnalyzer.analyze("https://a.com/p?trackid=123&q=1").facts
        val c = UrlAnalyzer.cleanup(f, removeParams = setOf("trackid"))
        assertEquals("https://a.com/p?q=1", c.url)
        assertEquals(1, c.removals.size)
        assertEquals("trackid", c.removals.first().token)
    }

    @Test
    fun `extraTracking treats custom names like built-ins`() {
        val f = UrlAnalyzer.analyze("https://a.com/p?trackid=123&q=1").facts
        val c = UrlAnalyzer.cleanup(f, extraTracking = setOf("trackid"))
        assertEquals("https://a.com/p?q=1", c.url)
        assertEquals("You marked this parameter as tracking", c.removals.first().detail)
    }

    @Test
    fun `keepParams still wins over extraTracking`() {
        val f = UrlAnalyzer.analyze("https://a.com/p?trackid=123").facts
        val c = UrlAnalyzer.cleanup(f, keepParams = setOf("trackid"), extraTracking = setOf("trackid"))
        assertEquals("https://a.com/p?trackid=123", c.url)
        // Listed (it IS flagged) but kept by explicit user choice.
        assertEquals(1, c.removals.size)
    }

    @Test
    fun `cleanup lists every removal with category`() {
        val c = UrlAnalyzer.cleanup(UrlAnalyzer.parse("https://u:pw@a.com/p?utm_source=x&id=2&fbclid=z"))
        assertEquals("https://a.com/p?id=2", c.url)
        assertEquals(
            listOf(CleanupCategory.CREDENTIALS, CleanupCategory.TRACKING_PARAM, CleanupCategory.TRACKING_PARAM),
            c.removals.map { it.category },
        )
        assertEquals(listOf("u:pw", "utm_source", "fbclid"), c.removals.map { it.token })
    }

    @Test
    fun `cleanup keepParams keeps opted-in tracking param`() {
        val c = UrlAnalyzer.cleanup(
            UrlAnalyzer.parse("https://a.com/p?utm_source=x&id=2"),
            keepParams = setOf("utm_source"),
        )
        assertEquals("https://a.com/p?utm_source=x&id=2", c.url)
        // Still listed as a removal (it IS tracking), but kept by user choice.
        assertEquals(1, c.removals.size)
    }

    @Test
    fun `cleanup keepCredentials restores userinfo`() {
        val c = UrlAnalyzer.cleanup(UrlAnalyzer.parse("https://u:pw@a.com/p"), keepCredentials = true)
        assertEquals("https://u:pw@a.com/p", c.url)
        assertEquals(1, c.removals.size)
    }

    @Test
    fun `cleanup on clean URL removes nothing`() {
        val c = UrlAnalyzer.cleanup(UrlAnalyzer.parse("https://a.com/p?id=2"))
        assertEquals("https://a.com/p?id=2", c.url)
        assertTrue(c.removals.isEmpty())
    }

    // ---------- verdict aggregation ----------

    @Test
    fun `worst severity across signals`() {
        val v = UrlAnalyzer.analyze("http://google.com@192.168.0.1:9000/a/b/c/d/e/f/g/h?utm_source=x").verdict
        assertEquals(DANGER, v.worst) // credentials dominate
        assertTrue(v.has(CREDENTIALS_IN_URL))
        assertTrue(v.has(IP_LITERAL_HOST))
        assertTrue(v.has(NONSTANDARD_PORT))
        assertTrue(v.has(TRACKING_PARAMS))
    }

    @Test
    fun `clean URL yields empty verdict`() {
        val v = UrlAnalyzer.analyze("https://example.com/a?q=1").verdict
        assertNull(v.worst)
        assertTrue(v.signals.isEmpty())
    }

    @Test
    fun upgradeScheme_rewrites_http_to_https_and_drops_port_80() {
        val f = UrlAnalyzer.analyze("http://example.com:80/a?utm_source=x").facts
        val upgraded = UrlAnalyzer.upgradeScheme(f)
        assertEquals("https://example.com/a", upgraded.url)
    }

    @Test
    fun upgradeScheme_leaves_https_untouched() {
        val f = UrlAnalyzer.analyze("https://example.com/a").facts
        assertEquals("https://example.com/a", UrlAnalyzer.upgradeScheme(f).url)
    }

    @Test
    fun upgradeScheme_keeps_nonstandard_ports() {
        val f = UrlAnalyzer.analyze("http://example.com:8080/a").facts
        assertEquals("https://example.com:8080/a", UrlAnalyzer.upgradeScheme(f).url)
    }

    @Test
    fun upgradeScheme_keeps_tracking_removals_in_tact() {
        val f = UrlAnalyzer.analyze("http://example.com/a?utm_source=x").facts
        val up = UrlAnalyzer.upgradeScheme(f)
        assertEquals("https://example.com/a", up.url)
        assertTrue(up.removals.any { it.category == com.dav3.linksentry.domain.model.CleanupCategory.TRACKING_PARAM })
    }
}
