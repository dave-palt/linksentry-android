package com.dav3.linksentry.domain.analyze

/**
 * Pulls the first scheme'd URL out of arbitrary shared text.
 *
 * Used for ACTION_SEND / ACTION_PROCESS_TEXT intake: apps share prose like
 * "check this https://example.com/x out" and LinkSentry inspects the URL
 * inside it. Deliberately conservative:
 * - only `http://` and `https://` (and case variants) are recognized;
 * - the match stops at whitespace — we never guess path boundaries in prose.
 */
object SharedTextExtractor {

    private val URL_RE = Regex("(?i)\\bhttps?://[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]+")

    /** First scheme'd URL in [text], or null when there is none. */
    fun firstUrl(text: String?): String? = text?.let { URL_RE.find(it)?.value }
}
