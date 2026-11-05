package com.dav3.linksentry.domain.laya

import com.dav3.linksentry.domain.model.RiskSignal
import com.dav3.linksentry.domain.model.Severity
import com.dav3.linksentry.domain.model.SignalId
import com.dav3.linksentry.domain.model.UrlFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkRiskQuestionsTest {

    private fun facts(
        raw: String = "https://example.com/login",
        host: String = "example.com",
        rawHost: String = host,
        scheme: String = "https",
        path: String = "/login",
        userInfo: String? = null,
        port: Int = 443,
    ) = UrlFacts(
        raw = raw,
        scheme = scheme,
        userInfo = userInfo,
        host = host,
        rawHost = rawHost,
        port = port,
        effectivePort = port,
        path = path,
        params = emptyList(),
        fragment = null,
        isIpLiteral = host.all { it.isDigit() || it == '.' },
        hasParseError = false,
    )

    @Test
    fun `question set has the expected shape and order`() {
        val all = LinkRiskQuestions.all()
        assertEquals(listOf("phishing", "scam", "malware", "suspicion"), all.map { it.first })
        assertEquals(listOf("noul", "noul", "noul", "score"), all.map { it.second["type"] })
        // Every schema is acceptable to the upstream normalizer.
        all.forEach { (_, schema) -> LayaPromptBuilder.normalize(schema) }
    }

    @Test
    fun `state carries url, host, scheme and non-default extras only`() {
        val state = LinkRiskQuestions.buildState(
            facts(userInfo = "user:pass", path = "/login", port = 8080, scheme = "http"),
            signals = listOf(RiskSignal(SignalId.CREDENTIALS_IN_URL, Severity.DANGER)),
        )
        assertEquals("https://example.com/login", state["url"])
        assertEquals("example.com", state["host"])
        assertEquals("http", state["scheme"])
        assertEquals("/login", state["path"])
        assertEquals("user:pass", state["embedded_credentials"])
        assertEquals(8080, state["port"])
        assertEquals("credentials embedded in the url", state["detected_signals"])
    }

    @Test
    fun `default ports and trivial paths are omitted`() {
        val state = LinkRiskQuestions.buildState(facts(path = "/"), signals = emptyList())
        assertNull(state["port"])
        assertNull(state["path"])
        assertNull(state["detected_signals"])
        assertNull(state["embedded_credentials"])
    }

    private fun noul(p: Double) = mapOf<String, Any?>("noul" to p, "confidence" to maxOf(p, 1 - p))

    @Test
    fun `verdict mapping reads all four answers and derives the summary`() {
        val verdict = LinkRiskQuestions.verdictFrom(
            mapOf(
                "phishing" to noul(0.93),
                "scam" to noul(0.10),
                "malware" to noul(0.02),
                "suspicion" to mapOf<String, Any?>("score" to 2.4),
            ),
        )
        assertEquals(0.93, verdict.phishing.probability, 1e-9)
        assertEquals(0.10, verdict.scam.probability, 1e-9)
        assertEquals(0.02, verdict.malware.probability, 1e-9)
        assertEquals(2.4, verdict.suspicion, 1e-9)
        assertEquals(0.93, verdict.maliciousProbability, 1e-9)
        assertEquals(LinkVerdict.Overall.LIKELY_MALICIOUS, verdict.overall)
        assertEquals(Severity.DANGER, LinkRiskQuestions.severityOf(verdict))
    }

    @Test
    fun `overall thresholds separate benign, suspicious and malicious`() {
        fun overall(phishing: Double, scam: Double, malware: Double, suspicion: Double) = LinkRiskQuestions.overall(LinkRiskQuestions.noulFrom(noul(phishing)), LinkRiskQuestions.noulFrom(noul(scam)), LinkRiskQuestions.noulFrom(noul(malware)), suspicion)

        assertEquals(LinkVerdict.Overall.APPEARS_BENIGN, overall(0.01, 0.05, 0.02, 0.4))
        assertEquals(LinkVerdict.Overall.SUSPICIOUS, overall(0.55, 0.10, 0.02, 1.0))
        assertEquals(LinkVerdict.Overall.SUSPICIOUS, overall(0.10, 0.10, 0.02, 1.8))
        assertEquals(LinkVerdict.Overall.LIKELY_MALICIOUS, overall(0.85, 0.10, 0.02, 1.0))
        assertEquals(LinkVerdict.Overall.LIKELY_MALICIOUS, overall(0.10, 0.10, 0.10, 2.8))
    }

    @Test
    fun `verdict mapping rejects malformed answers`() {
        try {
            LinkRiskQuestions.verdictFrom(
                mapOf(
                    "phishing" to mapOf<String, Any?>("confidence" to 1.0), // missing 'noul'
                    "scam" to noul(0.1),
                    "malware" to noul(0.1),
                    "suspicion" to mapOf<String, Any?>("score" to 1.0),
                ),
            )
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("noul"))
        }
    }
}
