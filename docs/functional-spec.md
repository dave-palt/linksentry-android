# Functional Spec

## Flows

### F1 — Intercept (default browser)
User taps an http/https link anywhere in the OS → LinkSentry opens
(singleTop) on Inspect. Nothing is fetched. The screen shows: host headline,
full raw URL, verdict card, signal list, URL breakdown, and the handler list.
If the link arrives while the app is open, `onNewIntent` re-runs inspection.

### F2 — Cleanup & dispatch
The inspect screen leads with a merged hero card ("You are about to open"):
host headline, editable raw URL, collapsible **URL breakdown** (whole-row
clickable section: scheme/host/path/query params with per-param Keep/Remove,
default-behavior labels, "Always remove" for unknown params), and a "Will
open" preview showing the exact effective URL (raw or cleaned) under the
**Open cleaned link** switch. Copy URL / Copy cleaned URL / Share ride the
handler list as pseudo entries (distinct icons). Tapping a handler opens that
app via an **explicit** intent (component set). DANGER links hide the handler
list behind the danger gate (F6).

### F3 — Danger gate
Links whose worst signal is DANGER replace the handler list with a red
confirmation card. "Proceed" stores a per-signal-set override; "Trust this
site" stores a per-host override — either can be revoked later (in-link or in
Settings). While gated, the last-app icon in History is disabled.

### F4 — Manual inspect
Launcher entry opens the Inspect tab in Manual mode: text field + role
banner ("Set as default browser" → system Default apps screen) when LinkSentry
isn't the default browser.

### F5 — History
Every URL is recorded **once** (unique on URL): re-inspects refresh severity,
opens bump `openCount` + last-opened timestamp and record the handler app;
copy/share update the last action without touching the count. List shows
newest first (cap 200 rows), tap to re-inspect, per-row delete, **filter by
URL/host content** with **Delete all found** (both confirmed), plus Clear all
with confirmation. Rows lead with the **last-handler icon** — tap to re-open
directly (disabled for DANGER links unless host/signal-set whitelisted);
`opened 3× · last opened 5m ago` when opened more than once. Pruning by
retention happens on write.

### F6 — Settings
- Default-browser status card + button to system Default apps settings.
- Record history toggle (default on).
- Retention: 1 / 7 / 30 days, or Forever (default 1 month).
- Theme: System / Light / Dark (default System).
- Handler layout: List / Grid.
- **Custom tracking params** — teach LinkSentry your own params (comma or
  newline separated); they're stripped in the cleaned URL and flagged in the
  breakdown like built-in trackers. Managed under Settings.
- **Allowed dangerous links** — revoke stored danger-gate overrides.
- The screen scrolls (bottom rows always reachable).

## Risk signals

| Signal | Severity | Trigger |
|---|---|---|
| Credentials in URL | DANGER | `user[:pass]@` present (everything before `@` is decoy) |
| Dangerous scheme | DANGER | `javascript:` `data:` `intent:` `file:` `content:` |
| Punycode host | DANGER | host label starting `xn--` |
| IP-literal host | WARN | IPv4/IPv6 host |
| Non-standard port | WARN | port ≠ scheme default |
| Shortener host | WARN | bit.ly, t.co, tinyurl.com, goo.gl, is.gd, ow.ly, buff.ly, cutt.ly, rebrand.ly, tiny.cc, linktr.ee (+subdomains) |
| Dotless host | WARN | host without a dot |
| Mixed-case host | INFO | raw host not lowercase |
| Explicit default port | INFO | `:80`/`:443` spelled out |
| Tracking params | INFO | `utm_*`, fbclid, gclid, mc_eid, mc_cid, ref, igshid, si |
| Very long URL | INFO | > 120 chars |
| Deep path | INFO | > 6 segments |

Verdict headline = worst severity. No signals → "No obvious red flags" with
explicit copy that this does NOT mean the link is safe.

Invalid input → "Not a valid URL" state; nothing opens.

## Privacy

- Zero permissions (not even INTERNET). No network stack in the app.
- History stored only in local Room DB; `allowBackup=false`.
