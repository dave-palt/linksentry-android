package com.dav3.linksentry.domain.model

/**
 * Scripted guided tour (Immich-style "try the demo"): auto-starts on first
 * launch and walks the user through every Inspect section using FAKE data —
 * fake handler apps, fake history rows — so any number of scenarios can be
 * shown without a real link at hand. Tour actions are sandboxed by the
 * ViewModel: nothing launches, nothing persists.
 *
 * The URLs themselves are real strings analyzed by the REAL analyzer, so
 * signals/cleanup shown during the tour are honest.
 */
data object DemoTour {

    /** Link used for act 1 of the tour (tracking/cleanup story). */
    const val URL_CLEAN: String =
        "https://www.example-shop.com/deals/laptop?utm_source=newsletter&utm_medium=email&fbclid=AbC123xYz&gclid=EFGH789"

    /** Link used for act 2 of the tour (danger-gate story). */
    const val URL_DANGER: String = "http://admin:hunter2@192.168.1.5:8080/login"

    /** Which screen section a step points at (drives highlight + scroll). */
    enum class Target { WELCOME, HERO, BREAKDOWN, CLEANUP, SWITCH, SIGNALS, GATE, APPS, FINISH }

    data class Step(
        val target: Target,
        val title: String,
        val body: String,
        val url: String,
    )

    val steps: List<Step> = listOf(
        Step(
            target = Target.WELCOME,
            title = "Welcome to LinkSentry",
            body = "This short tour shows everything you can do here. All data in it is fake - " +
                "nothing you tap will open, copy, or be saved.",
            url = URL_CLEAN,
        ),
        Step(
            target = Target.HERO,
            title = "Where every link starts",
            body = "The card shows who you are about to open: the site, the full link, and a " +
                "color for how suspicious it looks. Tap the link text to edit it.",
            url = URL_CLEAN,
        ),
        Step(
            target = Target.BREAKDOWN,
            title = "See inside the link",
            body = "The URL breakdown splits the link into scheme, host, port, path and every " +
                "parameter - trackers are marked with a dot.",
            url = URL_CLEAN,
        ),
        Step(
            target = Target.CLEANUP,
            title = "Clean it before you open it",
            body = "Tracking parameters and embedded credentials can be stripped. Use " +
                "Keep/Remove on each part to control exactly what is removed.",
            url = URL_CLEAN,
        ),
        Step(
            target = Target.SWITCH,
            title = "Open the cleaned version",
            body = "Flip this switch and watch the Will-open link change - handlers will then " +
                "open the cleaned URL instead of the original.",
            url = URL_CLEAN,
        ),
        Step(
            target = Target.SIGNALS,
            title = "What we noticed",
            body = "Every risk signal found, with its severity. LinkSentry never calls a link " +
                "safe - it shows you the facts to decide.",
            url = URL_CLEAN,
        ),
        Step(
            target = Target.APPS,
            title = "Open with the app you choose",
            body = "Your apps, browsers first-class. Try one - in the demo a tap only tells " +
                "you what would happen.",
            url = URL_CLEAN,
        ),
        Step(
            target = Target.HERO,
            title = "Now a nastier link",
            body = "This one embeds credentials, points at a raw IP address and an odd port - " +
                "classic phishing shape.",
            url = URL_DANGER,
        ),
        Step(
            target = Target.GATE,
            title = "The danger gate",
            body = "For dangerous links the app list stays hidden until you acknowledge the " +
                "warning - no accidental taps. You can still proceed, on purpose.",
            url = URL_DANGER,
        ),
        Step(
            target = Target.FINISH,
            title = "That is the core of it",
            body = "History keeps the links you inspected, Settings tunes the rules. This tour " +
                "will not show again - replay it anytime from Settings.",
            url = URL_DANGER,
        ),
    )

    /** Fake handler apps shown during the tour (nothing real resolves). */
    val fakeHandlers: List<HandlerApp> = listOf(
        HandlerApp(
            packageName = "com.brave.browser",
            activityName = "com.brave.browser.BrowserActivity",
            label = "Brave",
            isBrowser = true,
        ),
        HandlerApp(
            packageName = "org.mozilla.firefox",
            activityName = "org.mozilla.firefox.App",
            label = "Firefox",
            isBrowser = true,
        ),
        HandlerApp(
            packageName = PseudoHandler.COPY,
            activityName = "",
            label = "Copy link",
            isBrowser = false,
        ),
        HandlerApp(
            packageName = PseudoHandler.SHARE,
            activityName = "",
            label = "Share link",
            isBrowser = false,
        ),
    )

    /** Fake history rows for the first-open History demo. */
    fun fakeHistory(now: Long): List<LinkRecord> = listOf(
        LinkRecord(
            id = -1,
            url = URL_CLEAN,
            host = "www.example-shop.com",
            worstSeverity = Severity.INFO,
            action = HistoryAction.OPENED_WITH,
            timestamp = now - 2 * 60_000,
            openCount = 3,
            lastAppPackage = "com.brave.browser",
            lastAppActivity = "com.brave.browser.BrowserActivity",
            lastAppLabel = "Brave",
        ),
        LinkRecord(
            id = -2,
            url = "https://bit.ly/3xY2zAb",
            host = "bit.ly",
            worstSeverity = Severity.WARN,
            action = HistoryAction.INSPECTED,
            timestamp = now - 45 * 60_000,
            openCount = 0,
        ),
        LinkRecord(
            id = -3,
            url = URL_DANGER,
            host = "192.168.1.5",
            worstSeverity = Severity.DANGER,
            action = HistoryAction.COPIED_CLEANED,
            timestamp = now - 3 * 3_600_000,
            openCount = 1,
        ),
        LinkRecord(
            id = -4,
            url = "https://en.wikipedia.org/wiki/URL_shortening",
            host = "en.wikipedia.org",
            worstSeverity = null,
            action = HistoryAction.SHARED,
            timestamp = now - 26 * 3_600_000,
            openCount = 1,
        ),
        LinkRecord(
            id = -5,
            url = "https://news.example.org/story?id=42",
            host = "news.example.org",
            worstSeverity = Severity.INFO,
            action = HistoryAction.INSPECTED,
            timestamp = now - 3 * 86_400_000,
            openCount = 0,
        ),
    )
}
