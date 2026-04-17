# 🔭 NetScope

> **The Proxyman for Android.** On-device HTTP inspector for debug builds — no proxy, no SSL pinning bypass, no certificate install. Plug-and-play with OkHttp.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.nidhal92/netscope-core?label=Maven%20Central&color=0098C8)](https://central.sonatype.com/search?q=io.github.nidhal92)
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
| `netscope-ui` | Compose UI — list, detail, settings screens | debug |
| `netscope-notification` | Persistent status-bar notification with live stats | debug (optional) |
| `netscope-no-op` | Zero-overhead release stub — same API, no-ops | release |

---

## Installation

### 1. Add the dependencies

**`libs.versions.toml`**
```toml
[versions]
netscope = "0.3.1"

[libraries]
netscope-core         = { group = "io.github.nidhal92", name = "netscope-core",         version.ref = "netscope" }
netscope-ui           = { group = "io.github.nidhal92", name = "netscope-ui",           version.ref = "netscope" }
netscope-notification = { group = "io.github.nidhal92", name = "netscope-notification", version.ref = "netscope" }
netscope-no-op        = { group = "io.github.nidhal92", name = "netscope-no-op",        version.ref = "netscope" }
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
- ✅ Stats bar — total count · error count · average response time
- ✅ Animated item transitions and filter chip fade-in/out

### UI — Transaction detail
- ✅ Four tabs: **Overview** · **Request** · **Response** · **Timing**
- ✅ Directional slide animation between tabs
- ✅ JSON and XML syntax highlighting
- ✅ Horizontal timing bar — DNS / Connect / SSL / TTFB / Download phases
- ✅ One-tap copy on any field (URL, headers, body, curl command)
- ✅ Copy all · Share full summary actions
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
NetScopeViewModel            ← StateFlow, search + filter + sort
    │
    ▼
Compose UI                   ← TransactionListScreen / TransactionDetailScreen
```

### Module dependency graph

```
        [netscope-ui]
             │ api
             ▼
      [netscope-core]          [netscope-notification]
             │                          │ api
             │◄─────────────────────────┘
             │
    [netscope-no-op]  ← replaces netscope-core in release builds
```

---

## Changelog

### v0.3.0
- Settings screen — theme picker, all capture/privacy/interface settings, danger zone
- Light theme (default) + dark theme + system follow — status-bar icons sync correctly
- Runtime config updates — settings apply instantly without restarting OkHttp
- Shake-to-open toggle wired to live detector (start/stop immediately)
- Notification style: Off / Minimal / Detailed — applied on next transaction event
- Network phase timing tab with horizontal bar (DNS / Connect / TLS / TTFB / Download)
- Directional slide animation between detail tabs
- One-tap copy per field + snackbar feedback
- `NotificationStyle` enum promoted to `netscope-core` (accessible from notification module)
- ProGuard consumer rules added to all modules
- GitHub Actions publish workflow

### v0.2.0
- `netscope-notification` module — persistent silent notification with live stats
- JSON / XML syntax highlighting in request and response bodies
- Filter chips (method + status class) in a single horizontally scrollable row
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
| **v0.4.0** | Export as HAR / cURL from the UI, share sheet integration |
| **v0.5.0** | Mock response rules — intercept and return fixture JSON |
| **v1.0.0** | Ktor plugin, desktop companion app |

---

## Requirements

| | |
|---|---|
| Min SDK | 23 (Android 6.0) |
| OkHttp | 4.x or 5.x (`compileOnly` — your version is used) |
| Kotlin | 1.9+ |
| Jetpack Compose | BOM 2024.x+ |
| Room | 2.6+ |

---

## License

```
Copyright 2024 Nidhal Belhaj Salem

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
