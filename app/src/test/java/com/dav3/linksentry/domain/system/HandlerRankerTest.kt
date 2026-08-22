package com.dav3.linksentry.domain.system

import com.dav3.linksentry.domain.model.HandlerApp
import org.junit.Assert.assertEquals
import org.junit.Test

class HandlerRankerTest {

    private val chrome = HandlerApp("com.android.chrome", "c.Main", "Chrome", true)
    private val firefox = HandlerApp("org.mozilla.firefox", "f.Main", "Firefox", true)
    private val youtube = HandlerApp("com.google.android.youtube", "y.Main", "YouTube", false)
    private val handlers = listOf(chrome, firefox, youtube)

    @Test
    fun no_usage_defaults_to_browsers_first_then_label() {
        val ranked = HandlerRanker.rank(handlers, usage = emptyList(), host = "example.com", scheme = "https")
        assertEquals(listOf(chrome, firefox, youtube), ranked)
    }

    @Test
    fun domain_usage_floats_most_recently_used_first() {
        // youtube used most recently for this domain; chrome used earlier.
        val usage = listOf(
            HandlerUsage(HandlerRanker.domainKey("example.com"), "com.google.android.youtube", count = 2, lastUsed = 200),
            HandlerUsage(HandlerRanker.domainKey("example.com"), "com.android.chrome", count = 5, lastUsed = 100),
        )
        val ranked = HandlerRanker.rank(handlers, usage, host = "example.com", scheme = "https")
        // Recency beats count within the domain tier.
        assertEquals(listOf(youtube, chrome, firefox), ranked)
    }

    @Test
    fun count_breaks_recency_ties_in_domain_tier() {
        val usage = listOf(
            HandlerUsage(HandlerRanker.domainKey("example.com"), "com.google.android.youtube", count = 9, lastUsed = 100),
            HandlerUsage(HandlerRanker.domainKey("example.com"), "com.android.chrome", count = 3, lastUsed = 100),
        )
        val ranked = HandlerRanker.rank(handlers, usage, host = "example.com", scheme = "https")
        assertEquals(listOf(youtube, chrome, firefox), ranked)
    }

    @Test
    fun scheme_usage_applies_when_no_domain_usage() {
        val usage = listOf(
            HandlerUsage(HandlerRanker.schemeKey("https"), "org.mozilla.firefox", count = 1, lastUsed = 50),
        )
        val ranked = HandlerRanker.rank(handlers, usage, host = "example.com", scheme = "https")
        // Firefox never used for this domain, but preferred for https overall.
        assertEquals(listOf(firefox, chrome, youtube), ranked)
    }

    @Test
    fun domain_usage_outranks_scheme_usage() {
        val usage = listOf(
            HandlerUsage(HandlerRanker.domainKey("example.com"), "com.android.chrome", count = 1, lastUsed = 10),
            HandlerUsage(HandlerRanker.schemeKey("https"), "org.mozilla.firefox", count = 9, lastUsed = 999),
        )
        val ranked = HandlerRanker.rank(handlers, usage, host = "example.com", scheme = "https")
        assertEquals(listOf(chrome, firefox, youtube), ranked)
    }

    @Test
    fun `app used for both domain and scheme appears once`() {
        val chrome = HandlerApp("com.android.chrome", "Chrome", "c", isBrowser = true, icon = null)
        val firefox = HandlerApp("org.mozilla.firefox", "Firefox", "f", isBrowser = true, icon = null)
        val handlers = listOf(chrome, firefox)
        val usage = listOf(
            HandlerUsage(HandlerRanker.domainKey("example.com"), chrome.packageName, 3, 200),
            HandlerUsage(HandlerRanker.schemeKey("https"), chrome.packageName, 3, 200),
        )
        val ranked = HandlerRanker.rank(handlers, usage, host = "example.com", scheme = "https")
        assertEquals(listOf(chrome, firefox), ranked)
        assertEquals(1, ranked.count { it.packageName == chrome.packageName })
    }

    @Test
    fun usage_for_other_domains_is_ignored() {
        val usage = listOf(
            HandlerUsage(HandlerRanker.domainKey("other.org"), "com.google.android.youtube", count = 4, lastUsed = 999),
        )
        val ranked = HandlerRanker.rank(handlers, usage, host = "example.com", scheme = "https")
        assertEquals(listOf(chrome, firefox, youtube), ranked)
    }
}
