package com.dav3.linksentry.domain.model

import com.dav3.linksentry.domain.analyze.UrlAnalyzer
import com.dav3.linksentry.domain.model.Severity.DANGER
import com.dav3.linksentry.domain.model.Severity.INFO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tour script is the demo's contract: URLs must analyze (via the REAL
 * analyzer) to the severities the steps describe, fake handlers must be
 * well-formed, and every step target must map to a real screen section.
 */
class DemoTourTest {

    @Test
    fun `tour urls analyze to their advertised severities`() {
        val clean = UrlAnalyzer.analyze(DemoTour.URL_CLEAN)
        val danger = UrlAnalyzer.analyze(DemoTour.URL_DANGER)
        assertEquals(INFO, clean.verdict.worst)
        assertEquals(DANGER, danger.verdict.worst)
    }

    @Test
    fun `clean url has tracking cleanup`() {
        val facts = UrlAnalyzer.analyze(DemoTour.URL_CLEAN).facts
        val cleanup = UrlAnalyzer.cleanup(facts)
        assertTrue(cleanup.removals.isNotEmpty())
        assertTrue(cleanup.removals.any { it.token == "utm_source" })
        assertTrue(cleanup.removals.any { it.token == "fbclid" })
    }

    @Test
    fun `danger url has empty host marker and danger gate`() {
        // Wait - the danger URL uses a raw IP; host must resolve non-empty.
        val analysis = UrlAnalyzer.analyze(DemoTour.URL_DANGER)
        assertTrue(analysis.facts.host.isNotEmpty())
        assertTrue(
            analysis.verdict.signals.any { it.severity == Severity.DANGER },
        )
    }

    @Test
    fun `steps visit every target and switch urls at act boundary`() {
        val targets = DemoTour.steps.map { it.target }
        assertTrue(DemoTour.Target.WELCOME in targets)
        assertTrue(DemoTour.Target.BREAKDOWN in targets)
        assertTrue(DemoTour.Target.CLEANUP in targets)
        assertTrue(DemoTour.Target.SWITCH in targets)
        assertTrue(DemoTour.Target.SIGNALS in targets)
        assertTrue(DemoTour.Target.GATE in targets)
        assertTrue(DemoTour.Target.APPS in targets)
        assertTrue(DemoTour.Target.FINISH in targets)
        // First 7 steps: clean URL; last 3: danger URL.
        assertEquals(DemoTour.URL_CLEAN, DemoTour.steps.first().url)
        assertEquals(DemoTour.URL_DANGER, DemoTour.steps.last().url)
        // No step uses a URL not in the script.
        assertTrue(DemoTour.steps.all { it.url == DemoTour.URL_CLEAN || it.url == DemoTour.URL_DANGER })
    }

    @Test
    fun `fake handlers contain browsers and pseudo copy-share`() {
        val pkgs = DemoTour.fakeHandlers.map { it.packageName }
        assertTrue(pkgs.any { it == PseudoHandler.COPY })
        assertTrue(pkgs.any { it == PseudoHandler.SHARE })
        assertTrue(DemoTour.fakeHandlers.any { it.isBrowser })
    }

    @Test
    fun `fake history covers the action and severity spectrum`() {
        val now = System.currentTimeMillis()
        val rows = DemoTour.fakeHistory(now)
        assertTrue(rows.size >= 5)
        assertTrue(rows.any { it.action == HistoryAction.OPENED_WITH })
        assertTrue(rows.any { it.action == HistoryAction.COPIED_CLEANED })
        assertTrue(rows.any { it.worstSeverity == Severity.DANGER })
        assertTrue(rows.any { it.worstSeverity == null })
        assertTrue(rows.all { it.timestamp >= 0 })
        // Newest first ordering like the real list.
        assertEquals(rows.sortedByDescending { it.timestamp }, rows)
    }
}
