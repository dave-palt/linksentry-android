# Functional Spec

## Flows

### F1 — Intercept (default browser)
User taps an http/https link anywhere in the OS → LinkSentry opens
(singleTop) on Inspect. Nothing is fetched. The screen shows: host headline,
full raw URL, verdict card, signal list, URL breakdown, and the handler list.
If the link arrives while the app is open, `onNewIntent` re-runs inspection.

### F2 — Dispatch
Tapping a handler opens that app via an **explicit** intent (component set).
Also available: Copy URL, Copy cleaned URL (credentials + tracking params
stripped), Share (ACTION_SEND), Inspect another link.

### F3 — Manual inspect
Launcher entry opens the Inspect tab in Manual mode: text field + role
banner ("Set as default browser" → system Default apps screen) when LinkSentry
isn't the default browser.

### F4 — History
Every inspection is recorded (URL, host, worst severity, action, timestamp)
unless disabled in Settings. List shows newest first (cap 200 rows), tap to
re-inspect, Clear all with confirmation. Pruning by retention happens on
write.

### F5 — Settings
- Default-browser status card + button to system Default apps settings.
- Record history toggle (default on).
- Retention: 1 / 7 / 30 days, or Forever (default 30).
- Theme: System / Light / Dark (default System).

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
