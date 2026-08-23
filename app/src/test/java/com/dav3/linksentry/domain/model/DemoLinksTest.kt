package com.dav3.linksentry.domain.model

import com.dav3.linksentry.domain.analyze.UrlAnalyzer
import com.dav3.linksentry.domain.model.Severity.DANGER
import com.dav3.linksentry.domain.model.Severity.INFO
import com.dav3.linksentry.domain.model.Severity.WARN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The demo must be honest: every sample link really produces the verdict
 * it advertises when run through the real analyzer. If an analyzer change
 * ever dulls a sample, this test fails and the demo gets re-curated.
 */
class DemoLinksTest {

    private val analyzer = UrlAnalyzer

    class DemoLinksTest {

        private val analyzer = UrlAnalyzer

        @Test
        fun trackingSampleCarriesTrackingSignal() {
            val verdict = analyzer.analyze(DemoLinks.TRACKING).verdict
            assertEquals(INFO, verdict.worst)
            assertTrue(verdict.signals.any { it.id == SignalId.TRACKING_PARAMS })
        }

        @Test
        fun shortenerSampleIsWarned() {
            val verdict = analyzer.analyze(DemoLinks.SHORTENER).verdict
            assertEquals(WARN, verdict.worst)
            assertTrue(verdict.signals.any { it.id == SignalId.SHORTENER_HOST })
        }

        @Test
        fun phishingSampleIsDanger() {
            val verdict = analyzer.analyze(DemoLinks.PHISHING).verdict
            assertEquals(DANGER, verdict.worst)
            assertTrue(verdict.signals.any { it.id == SignalId.CREDENTIALS_IN_URL })
            assertTrue(verdict.signals.any { it.id == SignalId.IP_LITERAL_HOST })
            assertTrue(verdict.signals.any { it.id == SignalId.NONSTANDARD_PORT })
        }

        @Test
        fun allSamplesAreListedWithUniqueUrlsAndLabels() {
            assertEquals(3, DemoLinks.all.size)
            assertEquals(3, DemoLinks.all.map { it.url }.toSet().size)
            assertEquals(3, DemoLinks.all.map { it.label }.toSet().size)
            assertEquals(
                listOf(DemoLinks.TRACKING, DemoLinks.SHORTENER, DemoLinks.PHISHING),
                DemoLinks.all.map { it.url },
            )
        }

        @Test
        fun `demo links are recognized by exact URL`() {
            DemoLinks.all.forEach { sample ->
                assertTrue(DemoLinks.isDemo(sample.url))
            }
        }

        @Test
        fun `tour URLs are demo links too`() {
            assertTrue(DemoLinks.isDemo(DemoTour.URL_CLEAN))
            assertTrue(DemoLinks.isDemo(DemoTour.URL_DANGER))
        }

        @Test
        fun `a real user URL is not a demo link`() {
            assertFalse(DemoLinks.isDemo("https://news.ycombinator.com/item?id=123"))
        }
    }

    @Test
    fun shortenerSampleIsWarned() {
        val verdict = analyzer.analyze(DemoLinks.SHORTENER).verdict
        assertEquals(WARN, verdict.worst)
        assertTrue(verdict.signals.any { it.id == SignalId.SHORTENER_HOST })
    }

    @Test
    fun phishingSampleIsDanger() {
        val verdict = analyzer.analyze(DemoLinks.PHISHING).verdict
        assertEquals(DANGER, verdict.worst)
        assertTrue(verdict.signals.any { it.id == SignalId.CREDENTIALS_IN_URL })
        assertTrue(verdict.signals.any { it.id == SignalId.IP_LITERAL_HOST })
        assertTrue(verdict.signals.any { it.id == SignalId.NONSTANDARD_PORT })
    }

    @Test
    fun allSamplesAreListedWithUniqueUrlsAndLabels() {
        assertEquals(3, DemoLinks.all.size)
        assertEquals(3, DemoLinks.all.map { it.url }.toSet().size)
        assertEquals(3, DemoLinks.all.map { it.label }.toSet().size)
        assertEquals(
            listOf(DemoLinks.TRACKING, DemoLinks.SHORTENER, DemoLinks.PHISHING),
            DemoLinks.all.map { it.url },
        )
    }
}
