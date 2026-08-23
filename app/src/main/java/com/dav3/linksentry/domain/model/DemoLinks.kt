package com.dav3.linksentry.domain.model

/**
 * Curated sample links for the demo / "try it" experience: a newcomer
 * without a link at hand (or without default-browser setup) can see what
 * LinkSentry does with one tap. Samples run through the REAL analyzer —
 * [DemoLinksTest] pins each URL to its advertised verdict so the demo
 * can never silently rot.
 *
 * Mirrors the Immich app's "try demo" idea: explore with sample content,
 * zero setup, zero network.
 */
data object DemoLinks {

    /** A normal shopping link littered with tracker parameters. */
    const val TRACKING: String =
        "https://www.example-shop.com/deals/laptop?utm_source=newsletter&utm_medium=email&fbclid=AbC123xYz&gclid=EFGH789"

    /** A shortener: destination unknowable before opening. */
    const val SHORTENER: String = "https://bit.ly/3xY2zAb"

    /** Classic phishing shape: embedded credentials + raw IP + odd port. */
    const val PHISHING: String = "http://admin:hunter2@192.168.1.5:8080/login"

    /** Samples in display order, each with a short label. */
    val all: List<DemoLink> = listOf(
        DemoLink(TRACKING, "Tracked shopping link"),
        DemoLink(SHORTENER, "Shortened link"),
        DemoLink(PHISHING, "Phishing-style link"),
    )
}

data class DemoLink(
    val url: String,
    val label: String,
)
