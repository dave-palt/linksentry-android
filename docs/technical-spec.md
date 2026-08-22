# Technical Spec

## Stack

Kotlin 2.4.10 · AGP 9.1.0 · Compose BOM 2026.06.01 · Hilt 2.60.1 ·
Room 2.8.4 · DataStore 1.1.1 · Navigation-Compose 2.8.5 · Timber 5.0.1 ·
JUnit4 + Robolectric 4.17-beta-2 (+ Turbine, coroutines-test) · Spotless/ktlint 1.4.1.
minSdk 26 · compile/targetSdk 37 · Java 17 · versionCode = epoch seconds.

Deliberately absent (never add): INTERNET permission, WebView, Custom Tabs,
Retrofit/OkHttp, Coil, WorkManager. Enforced by CI guardrails
(`dev-build.yml`): source manifest must declare zero `uses-permission`;
merged manifest must not contain `android.permission.INTERNET`; main
sourceset must not reference `WebView|CustomTabsIntent|HttpClient|openConnection`.

## Package layout

```
com.dav3.linksentry
├── LinkSentryApp.kt          # @HiltAndroidApp + Timber
├── MainActivity.kt           # singleTop; VIEW intake (onCreate + onNewIntent)
├── di/                       # AppModule (binds), DatabaseModule (Room)
├── domain/
│   ├── analyze/UrlAnalyzer.kt    # pure Kotlin parser + signals + cleanedUrl
│   ├── model/Models.kt           # UrlFacts, RiskSignal, Verdict, HandlerApp, …
│   ├── repository/               # HistoryRepository, SettingsRepository (ifaces)
│   └── system/                   # HandlerResolver, LinkActions, BrowserRoleChecker
├── data/local/              # Room (History*), DataStoreProvider, repo impls
└── ui/
    ├── inspect/             # InspectViewModel (StateFlow), InspectContent (stateless), SignalCatalog
    ├── history/             # HistoryScreen + VM
    ├── settings/            # SettingsScreen + VM
    ├── nav/                 # LinkSentryNavHost (3-tab bottom bar)
    └── theme/
```

## Manifest essentials

- **Zero `uses-permission`.** (Debug builds gain only the auto-injected
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` marker — inert.)
- `<queries>`: two `<intent>` signatures (VIEW + BROWSABLE + scheme http /
  https) for package visibility on API 30+.
- MainActivity: `singleTop`, `exported`, MAIN/LAUNCHER filter + VIEW filter
  with DEFAULT + BROWSABLE + **both** http and https schemes (both required
  for browser-app candidacy in system settings).

## Persistence

DataStore keys (`settings` store, single delegate in `DataStoreProvider`):

| Key | Type | Default | Notes |
|---|---|---|---|
| `record_history` | Boolean | true | |
| `retention_days` | Int | absent → 30 | explicit `0` = forever (null) |
| `theme` | String | `SYSTEM` | enum name |

Room `history` table (v5): `id PK autogen, url UNIQUE, host, severity?, action, openCount, lastAppPackage?, lastAppActivity?, lastAppLabel?, timestamp` — migration path v1→v2 (handler prefs), v2→v3 (danger overrides + `history` unique index collapse), v3→v4 (openCount + last-app columns), v4→v5 (normalize semantics: split inspect/open/action bumps; openCount reset for inspect-only rows). Legacy format reference (v1): `id PK autogen, url, host, severity?, action,
timestamp`. DAO: insert, observeAll (DESC, LIMIT 200), deleteOlderThan,
clear, count.

## Platform mechanics

- Handler enumeration: `queryIntentActivities(VIEW+BROWSABLE, MATCH_ALL)` →
  drop self, distinctBy package, label via `loadLabel`.
- Browser classification: second query against
  `https://linksentry-probe.invalid/` — only wildcard-host filters (true
  browsers) match.
- Launch: explicit `setClassName` intent + `FLAG_ACTIVITY_NEW_TASK`; never
  implicit (default-browser loop).
- Role check: resolve generic https intent (compare to own package) falling
  back to `RoleManager.isRoleHeld(ROLE_BROWSER)` on API 29+.

## CI

`dev-build.yml` (PR/push → develop): lint job (spotless + lintDebug) and
build job (testDebugUnitTest, assembleDebug, guardrails, APK artifact, dev
pre-release on push). PRs targeting `main` also run the checks.

`prod-build.yml` (push → main with `paths-ignore` for docs/README/workflow-only changes, or manual dispatch): reads `versionName` from
`app/build.gradle.kts`, decodes the signing keystore from the
`SIGNING_KEYSTORE_BASE64` secret, runs unit tests, builds signed
`bundleRelease` + `assembleRelease`, re-runs the security guardrails against
the **release** merged manifest, uploads AAB/APK artifacts (90 days), and
publishes a GitHub Release tagged `v<versionName>` with the APK + AAB.

Signing secrets: `SIGNING_KEYSTORE_BASE64` (the `.jks`, base64),
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
The keystore is PKCS12: store and key password are the same value. Local
release builds read the same variables from a gitignored `local.env`.
`app/release.jks` is gitignored — never commit it.

## Tests

- `UrlAnalyzerTest` — 33 pure tests (parse + 12 signals + cleaned URL).
- `LinkActionsTest` — explicit-intent construction (Robolectric).
- `HistoryAndSettingsTest` — DAO ops, retention gating, DataStore round-trip.
- `InspectViewModelTest` — intake, dispatch actions, history gating (fakes).
