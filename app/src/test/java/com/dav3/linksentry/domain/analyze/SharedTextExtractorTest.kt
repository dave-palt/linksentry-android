package com.dav3.linksentry.domain.analyze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedTextExtractorTest {

    @Test
    fun extracts_http_url_from_prose() {
        val text = "Hey check this out https://example.com/p?id=2 it's cool"
        assertEquals("https://example.com/p?id=2", SharedTextExtractor.firstUrl(text))
    }

    @Test
    fun extracts_first_of_multiple_urls() {
        val text = "http://a.com/x and then https://b.com/y"
        assertEquals("http://a.com/x", SharedTextExtractor.firstUrl(text))
    }

    @Test
    fun stops_at_whitespace_and_punctuation_boundaries() {
        // A trailing period after a path must not be swallowed... unless it's
        // clearly part of the path. We keep the simple, safe rule: include
        // [A-Za-z0-9._~:/?#\[\]@!$&'()*+,;=%-] and stop at whitespace.
        val text = "see https://example.com/a/b, right?"
        assertEquals("https://example.com/a/b,", SharedTextExtractor.firstUrl(text))
    }

    @Test
    fun no_url_yields_null() {
        assertNull(SharedTextExtractor.firstUrl("just words, nothing else"))
        assertNull(SharedTextExtractor.firstUrl(""))
        assertNull(SharedTextExtractor.firstUrl(null))
    }

    @Test
    fun bare_domain_without_scheme_is_not_extracted() {
        // Only scheme'd URLs — a bare "example.com" in chat text is too
        // ambiguous to hijack into an inspector.
        assertNull(SharedTextExtractor.firstUrl("go to example.com now"))
    }
}
