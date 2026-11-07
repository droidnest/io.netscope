# Changelog

All notable changes to NetScope will be documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versioning follows [Semantic Versioning](https://semver.org/).

---

## [0.7.4] — 2026-11-07

### Fixed
- Request and response bodies are readable in the light theme: they sit on a white block with their own
  light syntax colours, and the light accent and secondary text colours reach WCAG AA contrast

---

## [0.7.3] — 2026-11-05

### Fixed
- `netscope-ui` no longer calls `FlowRow`, so apps on Compose foundation 1.8+ no longer crash on the Settings
  and mock rule editor screens

---

## [0.7.2] — 2026-11-05

### Fixed
- `netscope-ui` declares `NetScopeActivity`, so apps no longer crash when they open the inspector. An
  `<activity>` entry an app added for it can be removed

---

## [0.7.1] — 2026-11-05

### Fixed
- Apps on Kotlin 2.0 and 2.1 build against NetScope again. The modules are compiled for Kotlin 2.0 and depend
  on `kotlin-stdlib` 2.0.21; 0.7.0 carried Kotlin 2.2 metadata and `kotlin-stdlib` 2.2.10, which those
  compilers reject as "compiled with an incompatible version of Kotlin"

---

## [0.7.0] — 2026-11-02

### Changed
- `netscope-ui` screens follow MVI: one `uiState` and `onIntent` per flow
- The detail screen follows the transaction live, and says so when it was deleted
- The Stats response-time chart counts completed requests only

### Added
- Unit tests for the use cases, mock rule matching and every ViewModel

### Fixed
- Settings saved in the Settings screen apply when the library starts
- Network timing is kept when the response is saved
- The detail screen loads from its route argument, so it survives the app being recreated
- The notification stops updating once it is dismissed
- A database left by a newer build is rebuilt instead of crashing
- `netscope-no-op` has the stubs release builds need to compile the README code
- The Settings screen shows the current version

### Removed
- **Breaking:** `NetScopeViewModel`, `FilterState`, `MockRulesViewModel` and the screen composables are
  no longer public
- **Breaking:** `GetTransactionsUseCase` and `GetTransactionByIdUseCase`; the other use cases moved to
  `io.netscope.domain.usecase.<area>`, and `SortOrder` to `io.netscope.model`

---

## [0.1.0] — 2024-XX-XX

### Added
- `NetScopeInterceptor` — OkHttp application interceptor
- Room database for local transaction storage
- `TransactionListScreen` — Compose UI with live search
- `TransactionDetailScreen` — tabbed Overview / Request / Response
- Header redaction (Authorization, Cookie, custom)
- Gzip response body decoding
- Auto-prune oldest transactions when limit is exceeded
- `netscope-no-op` release artifact — zero overhead, no dependencies

---

## Upcoming

### [0.2.0]
- Persistent notification with live request stats
- Advanced filter chips (method, status range, host)
- JSON syntax highlighting

### [1.0.0]
- Export as HAR / cURL
- Request timeline visualization (DNS, TLS, TTFB)
- Shake-to-open gesture
