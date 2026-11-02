# Changelog

All notable changes to NetScope will be documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versioning follows [Semantic Versioning](https://semver.org/).

---

## [Unreleased]

### Changed
- `netscope-ui` screens follow MVI: one `uiState` and `onIntent` per flow
- The detail screen follows the transaction live, and says so when it was deleted
- The Stats response-time chart counts completed requests only

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
