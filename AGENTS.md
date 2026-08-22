# AGENTS.md

Instructions for AI coding agents working on this repository.
Read this before making any changes.

## Project

LinkSentry — a zero-permission default-browser link inspector for Android.
Package: `com.dav3.linksentry` (debug suffix `.debug`). Kotlin + Jetpack
Compose + Hilt. GitHub: `dave-palt/linksentry`.

## The One Rule

**LinkSentry never gains network capability.** No `INTERNET` permission, no
WebView/Custom Tabs/HTTP client dependency, ever. CI fails the build if any
appears. If a feature seems to need the network, it does not belong in this
app.

## Build

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.20/libexec/openjdk.jdk/Contents/Home \
  ./gradlew clean spotlessApply spotlessCheck lintDebug assembleDebug --no-daemon --no-configuration-cache
```

JDK 17 required. Always `spotlessApply` before `spotlessCheck`.
Suppressed ktlint rules (root `build.gradle.kts`): `no-wildcard-imports`,
`function-naming`.

## Git Workflow

1. Branch off `origin/develop`: `feat/<description>` or `fix/<description>`.
2. Commit as `dave-palt` (`git@dav3.cc`).
3. PRs target `develop`. `main` is production-only (merged via PR).
4. NEVER force-push to `develop` or `main`.

## Architecture (one-liner)

```
UI (Compose) → ViewModel (StateFlow) → UrlAnalyzer (pure) / HandlerResolver (PackageManager)
                                    ↘ Room (history) · DataStore (settings)
```

- Single shared DataStore (`DataStoreProvider.kt`). Never create a second
  `preferencesDataStore` delegate — it crashes.
- URL launches are EXPLICIT intents only (`AndroidLinkActions.buildOpenIntent`).
  Never re-fire the implicit VIEW intent — as default browser it loops back
  into LinkSentry.
- `UrlAnalyzer` is pure Kotlin (no Android imports) — all URL risk logic and
  tests live there. Keep it that way.
- Handler resolution uses `PackageManager.MATCH_ALL` + the manifest
  `<queries>` block; self (`context.packageName`) is always filtered out.

## Docs Must Stay in Sync

Every user-facing change updates the matching doc in the same commit:

| Doc | Update when |
|---|---|
| `docs/overview.md` | Goals / non-goals change |
| `docs/functional-spec.md` | Flows, signals, settings change |
| `docs/technical-spec.md` | Deps, packages, DataStore keys, manifest, CI |
| `README.md` | Feature list, requirements |

## User Clarifications Log

Newest first. This section overrides the docs until they are fixed.

- **2026-08-22** — Project bootstrap. Name: **LinkSentry** (was "Link
  Preview" in the original plan). Architecture conventions inherited from
  `immich-android` (same build/git/docs patterns). minSdk 26, zero
  permissions, guardrails in `dev-build.yml`.
