# 🔭 NetScope

> **The Proxyman for Android.** On-device HTTP inspector for debug builds. Zero proxy setup. Zero SSL issues. Plug-and-play with OkHttp.

---

## Installation

```kotlin
// build.gradle.kts
debugImplementation("io.netscope:netscope-core:0.1.0")
debugImplementation("io.netscope:netscope-ui:0.1.0")
releaseImplementation("io.netscope:netscope-no-op:0.1.0")
```

## Setup (2 lines)

```kotlin
// In your NetworkModule / OkHttpClient builder
OkHttpClient.Builder()
    .addInterceptor(
        NetScope.interceptor(context) {
            maxTransactions(500)
            redactHeaders("Authorization", "X-Api-Key")
        }
    )
    .build()
```

## Open the UI

```kotlin
// From anywhere in your debug app
NetScopeActivity.launch(context)
```

---

## v0.1.0 — What's included

| Feature | Status |
|---|---|
| OkHttp interceptor | ✅ |
| Room local storage | ✅ |
| Transaction list screen (Compose) | ✅ |
| Transaction detail screen (Compose) | ✅ |
| Search / filter | ✅ |
| Header redaction | ✅ |
| Gzip response decoding | ✅ |
| Release no-op module | ✅ |

## Roadmap

- **v0.2.0** — Notification, advanced filters, JSON syntax highlight
- **v1.0.0** — Export HAR/cURL, timeline view, shake-to-open
- **v1.x** — Mock responses, Ktor plugin, desktop companion

---

## Modules

| Module | Purpose |
|---|---|
| `netscope-core` | Interceptor, Room DB, domain models, repository |
| `netscope-ui` | Compose UI screens, ViewModel, navigation |
| `netscope-no-op` | Release stub — zero overhead |

## Architecture

```
OkHttp Request
    → NetScopeInterceptor
    → TransactionParser
    → Room Database (TransactionDao)
    → Flow<List<NetTransaction>>
    → NetScopeViewModel (StateFlow)
    → Compose UI
```

## License

Apache 2.0
