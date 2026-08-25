# LinkSentry

A default-browser replacement that **never renders web content**. Every link
you tap lands in LinkSentry first: see exactly where it points, what's
suspicious about it, and choose which app — if any — gets to open it.

## Why

Tapping a link hands control to whatever is on the other end. LinkSentry puts
a checkpoint between the tap and the load: the URL is decomposed and checked
**entirely on your device**, and nothing is fetched until you explicitly pick
an app to open it with.

**The app declares zero permissions — not even `INTERNET`.** It contains no
WebView, no HTTP client. It is *physically incapable* of loading a link,
leaking a URL, or phoning home. This is enforced in CI.

## Features

- **Default-browser interception** — set LinkSentry as your browser; every
  http/https tap opens the Inspect screen instead of a page.
- **URL risk signals (local, no cloud):** embedded credentials
  (`http://google.com@evil.com`), IP-literal hosts, non-standard ports,
  punycode/homograph hosts, URL shorteners, tracking parameters
  (`utm_*`, `fbclid`, …), dangerous schemes (`javascript:`, `data:`, `intent:`,
  `file:`), and more. Absence of signals is never presented as "safe".
- **Open-with chooser** — the real list of installed apps that can handle the
  link, browsers flagged; launches use explicit intents only.
- **Search all apps** — when an app doesn't declare support for a link (or
  only matches narrower paths), search every launchable app by name or
  package and open it explicitly anyway.
- **Live handler list** — the "Open with" list is re-resolved every time the
  screen resumes, so newly installed apps or changed link-handling defaults
  appear without re-submitting the link.
- **Copy / Copy cleaned / Share** — cleaned strips credentials + tracking
  parameters.
- **Manual inspect** — paste or type any URL.
- **Try-demo samples** — one-tap sample links (tracked / shortened / phishing-style) on the entry screen; zero setup, real analysis.
- **Guided intro tour** — first-run spotlight tour of the Inspect screen
  (auto-expands sections as a nudge; your taps always win); sandboxed demo
  actions, fake history rows on first History open, replayable from Settings.
- **Link cleanup** — per-param Keep/Remove, "always remove" personal rules,
  custom tracking params, and a live "will open" preview of the effective URL.
- **Enforce HTTPS** — one-tap per-link upgrade of http links before opening.
- **Per-link history opt-out** — "Keep out of history" keeps a specific link
  out of the local history (removes existing rows too).
- **Colored URL breakdown** — scheme/host/port rows risk-colored from the
  analyzer's own signals.
- **Danger gate** — DANGER links hide the handler list behind a confirmation
  card; per-host and per-signal-set overrides (revocable in Settings).
- **Local history** (Room, on-device only) with retention control, one row
  per URL (open count + last opened), content filter with delete-all-found,
  and tap-the-icon re-open in the last handler app.
- **No network. No analytics. No tracking. Zero permissions.**

## Screenshots

| Inspect (clean link) | Dangerous link (gated) |
|---|---|
| ![Inspect screen showing URL breakdown, cleanup controls, will-open preview and handler list](docs/images/inspectclean.png) | ![Danger screen showing risk signals behind the confirmation gate](docs/images/inspectdangerous.png) |

| History | Settings |
|---|---|
| ![History list with open counts, last-opened times and last-app icons](docs/images/history.png) | ![Settings screen](docs/images/settings.png) |

*Rendered from Roborazzi Compose previews — the same snapshots CI verifies.*

## Install

Grab the latest signed APK from [Releases]. Dev builds install side-by-side
(`…debug` app id suffix, amber icon).

## Build

```bash
JAVA_HOME=<jdk17> ./gradlew clean spotlessApply spotlessCheck lintDebug assembleDebug --no-daemon --no-configuration-cache
```

JDK 17 required. See [docs/technical-spec.md](docs/technical-spec.md).

MIT License.

[Releases]: ../../releases
