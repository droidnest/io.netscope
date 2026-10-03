# Changelog

All notable changes to NetScope will be documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versioning follows [Semantic Versioning](https://semver.org/).

---

## [Unreleased]

### Fixed
- **Memory** — the transaction list no longer loads request/response bodies (fixes OOM with large captures); search now runs in SQLite.
- **Capture** — uploads are captured with bounded memory; one-shot and duplex request bodies are no longer consumed.
- **Capture** — Server-Sent Events responses are skipped, truncated gzip bodies are decoded partially, and body size is reported in bytes.
- **Timing** — DNS/connect/TLS/TTFB data is no longer wiped when the response is written.
- **Database** — schema v3 adds an index on `requestDate` for faster list queries and pruning.
- **Redaction** — header redaction is case-insensitive for directly built `NetScopeConfig`s too.
- **Settings** — capture/privacy values are persisted in core and re-applied on launch; redaction is the union of code and Settings (Settings can never remove a redacted header).
- **UI** — the detail screen live-updates and survives process death; `NetScopeActivity` no longer crashes after process death (new `NetScope.ensureInitialized(context)`).
- **Mocking docs** — `NetScope.mockInterceptor()` must be added **after** `NetScope.interceptor()` so mocked responses are recorded.
- **Notification** — `NetScopeNotificationManager.dismiss()` now stops updates (the notification no longer reappears on the next request); `init` is idempotent and thread-safe, and identical stats are not re-posted.
- **Export (security)** — cURL export shell-quotes the method, URL, every header and the body, so a `'` in captured data can no longer inject shell commands.
- **Export** — HAR `creator.version` reports the real library version instead of a hard-coded `0.3.1`.
- **Performance** — heavy body formatting, stats and HAR generation moved off the main thread.
- **Timing** — the Timing tab no longer crashes on 0 ms phases, and TLS time is no longer double-counted in the timeline total.
- **Export** — HAR and full-summary sharing go through a file (FileProvider) instead of `EXTRA_TEXT`, so large captures no longer throw `TransactionTooLargeException`; exporting the visible list loads only those rows.
- **Search** — search is debounced, and header search matches real header values (headers are stored without Gson HTML escaping; DB v4 adds a plain-text header search column, migrated from v3).
- **UI** — the detail screen keeps its top bar and shows "no longer available" when the row is pruned; no stale or blank frames when switching transactions.
- **Stats** — pending requests are no longer counted as fast, failed requests have a duration, and the screen shows a filter indicator and a filter-aware empty state.
- **Capture** — response body size reflects the bytes actually read for chunked/gzip responses.
- **Init** — fixed a race where `ensureInitialized` could replace the host's redaction config.
- **Settings** — opening Settings no longer overrides a notification style set in code; the style is only written when the user picks one.

### Changed
- **`netscope-no-op` API parity (security)** — the release artifact now mirrors core's public API: `mockInterceptor()`, `mockRules`, `add/update/delete/toggleMockRule`, `config`, `updateConfig`, `ensureInitialized`, plus `MockRule` and `NetScopeConfig`. Release variants that use mocks now compile against no-op, so there's no reason to ship core (which writes production bodies to disk) in release. No-op now depends on `kotlinx-coroutines-core` (for the `mockRules` `StateFlow`).
- **`TransactionRepository`** — `observeAll()` is deprecated and again returns bodies; use the new `observeSummaries()` for list screens.

### Added
- `NetScopeExporter.shareAsFile` — shares an export as a file through NetScope's FileProvider.
- `TransactionRepository.observeSummaries()`, `getByIds` and `updateResponseSize`.
- `netscope-no-op` gains `NetScopeSettings` (defaults only, nothing persisted) for release parity.
- Unit and Robolectric integration tests, and a pull-request CI workflow (`testDebugUnitTest assembleDebug`).

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
