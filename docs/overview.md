# Overview

## What LinkSentry is

A native Android app that replaces the default browser with a **link
inspector**. It registers for http/https `ACTION_VIEW` intents, shows the
decomposed URL with local risk signals, and lets the user dispatch the link
to an installed app of their choice via an explicit intent.

## Goals

- Let users see exactly what a link is before anything loads.
- Make the "cannot fetch anything" property structural: zero permissions, no
  web engine, enforced by CI.
- Provide a genuinely useful open-with chooser (real browsers distinguished
  from deep-link handlers).
- Keep all analysis local and offline.
- Local-only history with user-controlled retention.

## Non-goals

- Rendering web content of any kind (no WebView, no Custom Tabs).
- Cloud lookups: URL blocklists, safe-browsing APIs, VirusTotal, WHOIS.
  (Would require the INTERNET permission — forbidden.)
- Expanding shortened links (requires network).
- Acting as a firewall or proxy for other apps' traffic.
- Self-update (no network; installs come from GitHub Releases).

## Threat model notes

LinkSentry raises *signals*, never verdicts of safety. A clean report means
"nothing locally recognizable looked wrong" — the UI copy says exactly that.
The product claim is friction + visibility, not protection.
