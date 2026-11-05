# 🔭 NetScope

> **The Proxyman for Android.** On-device HTTP inspector for debug builds — no proxy, no SSL pinning bypass, no certificate install. Plug-and-play with OkHttp.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.droidnest/netscope-core?label=Maven%20Central&color=00C8F0)](https://central.sonatype.com/search?q=io.github.droidnest)
[![API](https://img.shields.io/badge/minSdk-23-brightgreen)](https://developer.android.com/about/versions/marshmallow)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)

---

## Screenshots

| Transaction List | Transaction Detail | Settings |
|---|---|---|
| _request list with search, filter chips, stats bar_ | _tabs: Overview · Request · Response · Timing_ | _theme, capture, privacy, shake, notification_ |

---

## What's inside

| Module | Role | Include in |
|---|---|---|
| `netscope-core` | OkHttp interceptor, Room DB, domain models | debug |
| `netscope-ui` | Compose UI — list, detail, stats, mock rules, settings screens | debug |
| `netscope-export` | cURL, HAR and summary export, clipboard and share sheet | debug (comes with `netscope-ui`) |
| `netscope-notification` | Persistent status-bar notification with live stats | debug (optional) |
| `netscope-no-op` | Zero-overhead release stub — same API, no-ops | release |

---

## Installation

### 1. Add the dependencies

**`libs.versions.toml`**
```toml
[versions]
netscope = "0.7.3"

[libraries]
netscope-core         = { group = "io.github.droidnest", name = "netscope-core",         version.ref = "netscope" }
netscope-ui           = { group = "io.github.droidnest", name = "netscope-ui",           version.ref = "netscope" }
netscope-notification = { group = "io.github.droidnest", name = "netscope-notification", version.ref = "netscope" }
netscope-no-op        = { group = "io.github.droidnest", name = "netscope-no-op",        version.ref = "netscope" }
```

**`app/build.gradle.kts`**
```kotlin
dependencies {
    // Debug: full inspector
    debugImplementation(libs.netscope.core)
    debugImplementation(libs.netscope.ui)
    debugImplementation(libs.netscope.notification)  // optional

    // Release: silent no-op, ~2 KB added to APK
    releaseImplementation(libs.netscope.no-op)
}
```

### 2. Wire the interceptor

```kotlin
// NetworkModule.kt  (or wherever you build your OkHttpClient)
val client = OkHttpClient.Builder()
    .addInterceptor(
        NetScope.interceptor(context) {
            maxTransactions(500)          // prune oldest once limit is reached
            redactHeaders("Authorization", "X-Api-Key")  // replaced with ██████
            maxBodySize(250_000)          // bytes; bodies are truncated beyond this
        }
    )
    // Optional: applies the mock rules. Must come after NetScope.interceptor() so mocked calls are recorded
    .addInterceptor(NetScope.mockInterceptor())
    // Optional: captures DNS / TCP connect / TLS / TTFB per request
    .eventListenerFactory(NetScope.timingListenerFactory())
    .build()
```

### 3. Optional — persistent notification

```kotlin
// Application.onCreate()  (after building OkHttpClient)
NetScopeNotificationManager.init(
    context,
    launchActivity = NetScopeActivity::class.java,
)
```

### 4. Optional — shake-to-open

```kotlin
// Activity.onCreate()
val shakeDetector = NetScopeShakeDetector(this) {
    runOnUiThread { NetScopeActivity.launch(this) }
}
NetScope.registerShakeDetector(shakeDetector)
NetScope.setShakeEnabled(NetScopePreferences(this).shakeToOpen)

override fun onResume() { super.onResume(); shakeDetector.start() }
override fun onPause()  { super.onPause();  shakeDetector.stop()  }
```

### 5. Open the UI manually

```kotlin
NetScopeActivity.launch(context)
```

---

## Features

### Navigation
- ✅ **Bottom navigation bar** — Requests · Mock · Stats · Settings; slides in/out per route

### Analytics (Stats tab)
- ✅ Summary row — total, success %, error count, avg duration
- ✅ p50 / p95 / avg latency with adaptive color coding
- ✅ Status class stacked bar + per-class breakdown table (2xx / 3xx / 4xx / 5xx / Error)
- ✅ HTTP method distribution — proportional bars, count, percentage
- ✅ Response time histogram of completed requests — < 100ms / 100–300ms / 300ms–1s / > 1s buckets
- ✅ Follows the list's search and filters
- ✅ Top 5 slowest requests — tappable, ranked with duration badge
- ✅ Top 5 hosts by request count with mini inline bar

### Capture
- ✅ Every OkHttp request and response — method, URL, headers, body, status code
- ✅ Gzip-encoded responses decoded automatically
- ✅ Network phase timing: DNS · TCP connect · TLS handshake · TTFB
- ✅ Configurable body size limit (truncates, never crashes)
- ✅ Configurable max transaction count (oldest pruned automatically)

### UI — Transaction list
- ✅ Real-time Flow-driven list — new requests appear instantly
- ✅ Full-text search across URL, headers, and body
- ✅ Filter chips — by HTTP method (GET / POST / PUT / PATCH / DELETE) and status class (2xx / 3xx / 4xx / 5xx / ERR)
- ✅ Sort by time (newest first), duration (slowest first), or size (largest first)
- ✅ Stats bar — total (cyan) · errors (amber) · avg time (green) · size (purple)
- ✅ Animated item transitions and filter chip fade-in/out
- ✅ **Export all transactions as HAR** — share sheet integration

### UI — Transaction detail
- ✅ Four tabs: **Overview** · **Request** · **Response** · **Timing**
- ✅ Live — a pending request fills in as soon as its response and timing are saved
- ✅ Overview: request/response dcard sections + 3 mini stat cards (Duration · Size · Protocol) + Copy as cURL row
- ✅ Timing: Summary card (Started · Completed · Total) + phase bars (DNS=purple, TCP=cyan, TLS=pink, TTFB=amber) + stacked timeline bar + legend
- ✅ Directional slide animation between tabs
- ✅ JSON and XML syntax highlighting
- ✅ One-tap copy on any field (URL, headers, body, cURL command)
- ✅ Copy all · Share full summary · **Share as HAR** actions
- ✅ Snackbar confirmation on every copy

### Settings (all applied instantly at runtime)
- ✅ **Theme** — Light / Dark / Follow system (light is default)
- ✅ **Max transaction count** — 100 / 250 / 500 / 1 000 / 2 000 / 5 000
- ✅ **Body size limit** — 50 KB / 100 KB / 250 KB / 500 KB / 1 MB
- ✅ **Always read response body** — force-read even non-text content types
- ✅ **Header redaction rules** — add / remove headers redacted with ██████
- ✅ **Shake-to-open** — enable / disable the gesture at runtime
- ✅ **Notification style** — Off / Minimal (count only) / Detailed (count + errors + last code)
- ✅ **Clear all transactions** — confirmation dialog

### Privacy
- ✅ All data stays on-device in a local Room database
- ✅ Redacted headers never appear in logs, exports, or any network call
- ✅ No-op release build compiles to ~2 KB and makes zero network calls

---

## Architecture

```
OkHttp Request
    │
    ▼
NetScopeInterceptor          ← captures request, writes PENDING row
    │
    ▼
NetScopeTimingListenerFactory ← optional: DNS/TCP/TLS/TTFB phases
    │
    ▼
TransactionRepositoryImpl    ← Room DAO, Flow<List<NetTransaction>>
    │
    ▼
FilterTransactionsUseCase    ← search + filter + sort
    │
    ▼
TransactionsViewModel        ← one StateFlow<UiState<T>>, onIntent(intent)
    │
    ▼
Compose UI                   ← TransactionListScreen / StatsScreen
```

### Layers

| Layer | Module and package | Holds |
|---|---|---|
| Domain | `netscope-core`: `domain/repository`, `domain/usecase/<area>`, `model` | Repository interfaces, one use case per file, models |
| Data | `netscope-core`: `data/db`, `data/local`, `data/mapper`, `data/repository` | Room, SharedPreferences, repository implementations |
| UI | `netscope-ui`: one package per flow | ViewModels, intents, screen states, Compose screens |

The UI depends on use cases, never on a repository implementation. There is no DI framework: the `NetScope`
object holds the repositories, and each ViewModel's `Factory` builds the use cases from them.

### UI flows

Each flow has one ViewModel that exposes `uiState` and takes `onIntent(intent)`. Screens receive those two and
never see the ViewModel, and navigation and open dialogs are part of the state.

| Flow | Package | Screens | Scope |
|---|---|---|---|
| Transactions | `ui.transactions` | List, Stats | Navigation graph, because Stats shows the figures of the filtered list |
| Detail | `ui.detail` | Transaction detail | Destination |
| Settings | `ui.settings` | Settings | Destination |
| Mock rules | `ui.mockrules` | Rule list | Destination |
| Mock editor | `ui.mockeditor` | Rule editor | Destination |

### Module dependency graph

```
        [netscope-ui] ───────────► [netscope-export]
             │ api                        │
             ▼                            │
      [netscope-core] ◄───────────────────┘
             ▲
             │ api
    [netscope-notification]

    [netscope-no-op]  ← replaces the modules above in release builds
```

---

## Changelog

### v0.7.3
- **Compose 1.8+ apps** — `netscope-ui` no longer calls the experimental `FlowRow`, whose signature changed in Compose foundation 1.8, so Settings and the mock rule editor no longer crash with `NoSuchMethodError`. Tested on Compose 1.7, 1.8 and 1.12

### v0.7.2
- **Inspector opens in every app** — `netscope-ui` declares `NetScopeActivity` in its own manifest, so a shake or a notification tap no longer crashes with `ActivityNotFoundException`. Apps that declared the activity themselves can remove that entry

### v0.7.1
- **Kotlin 2.0 apps** — the modules are compiled for Kotlin 2.0 and depend on `kotlin-stdlib` 2.0.21, so an app on Kotlin 2.0 or newer builds against them. 0.7.0 required Kotlin 2.2

### v0.7.0
- **MVI architecture** — every screen now has one state and one intent entry point, with use cases split by area
- **Live transaction detail** — a pending request fills in as soon as its response is saved; a request deleted while open is reported
- **Saved Settings apply at startup** — from the first request of the next launch, before the UI is opened
- **Fixes** — network timing kept when the response is saved, detail screen restored after the app is recreated, notification stops updating once dismissed, no crash when the database is downgraded
- **First unit tests** — use cases, mock rule matching and every ViewModel
- **Breaking** — `NetScopeViewModel`, `FilterState` and the screen composables are no longer public; use cases and `SortOrder` moved packages (see [CHANGELOG.md](CHANGELOG.md))

### v0.6.0
- **Analytics screen** (Stats tab) — p50/p95/avg latency, status class distribution bar, HTTP method breakdown, response time histogram, top-5 slowest requests (tappable), top-5 hosts
- **Bottom navigation bar** — persistent 4-tab bar (Requests · Mock · Stats · Settings) replaces icon buttons in the top bar; animates in/out for overlay routes (detail, editor)
- **Transaction detail redesign** — Overview tab gains 3 mini stat cards + inline "Copy as cURL" row; Timing tab gains Summary card + colored phase dots + stacked timeline bar + legend
- **Design system refresh** — updated stat card colors (errors=amber, avg=green, size=purple), dcard-style headers with dividers in detail tabs

### v0.5.0
- **Mock response rules** — create rules that intercept matching requests and return fixture JSON (or any content type) without hitting the network
- Rules match by URL pattern (Contains / Exact / Regex), optional HTTP method filter, and can carry a configurable delay
- Full CRUD UI: list screen with per-rule toggle + delete, editor form for create/edit
- `NetScopeMockInterceptor` — add after `NetScopeInterceptor` in your OkHttp chain; mocked responses are still recorded in the transaction list
- Rules persisted across app restarts via SharedPreferences + Gson

### v0.4.0
- Dedicated **Timing tab** in transaction detail — timestamps, duration, and full network phase bar (DNS / Connect / TLS / TTFB)
- **Share as HAR** action added to the detail screen overflow menu (single-transaction HAR)
- **Export all as HAR** added to the transaction list screen overflow menu (full HTTP Archive of all visible transactions)
- **XML syntax highlighting** in request and response bodies (tags, attributes, values, comments, declarations)

### v0.3.0
- Settings screen — theme picker, all capture/privacy/interface settings, danger zone
- Light theme (default) + dark theme + system follow — status-bar icons sync correctly
- Runtime config updates — settings apply instantly without restarting OkHttp
- Shake-to-open toggle wired to live detector (start/stop immediately)
- Notification style: Off / Minimal / Detailed — applied on next transaction event
- Network phase timing bar in the Overview tab (DNS / Connect / TLS / TTFB)
- Directional slide animation between detail tabs
- One-tap copy per field + snackbar feedback
- `NotificationStyle` enum promoted to `netscope-core` (accessible from notification module)
- ProGuard consumer rules added to all modules
- GitHub Actions publish workflow

### v0.2.0
- `netscope-notification` module — persistent silent notification with live stats
- JSON syntax highlighting in request and response bodies
- Filter chips (method + status class)
- Sort menu — time / duration / size

### v0.1.0
- `NetScopeInterceptor` — OkHttp application interceptor
- Room database for local persistence
- Transaction list screen (Compose) with real-time Flow updates
- Transaction detail screen — Overview / Request / Response tabs
- Full-text search
- Header redaction
- `netscope-no-op` release stub

---

## Roadmap

| Version | Planned |
|---|---|
| **v0.8.0** | Network graph, export to Postman collection |
| **v1.0.0** | Ktor plugin, desktop companion app |

---

## Requirements

| | |
|---|---|
| Min SDK | 23 (Android 6.0) |
| OkHttp | 4.x or 5.x (`compileOnly` — your version is used) |
| Kotlin | 2.0+ (the modules are compiled for Kotlin 2.0) |
| Jetpack Compose | BOM 2024.x+ |
| Room | 2.6+ |

---

## License

```
Copyright 2024 droidnest (https://github.com/droidnest)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
