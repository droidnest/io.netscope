# Changelog

All notable changes to NetScope will be documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versioning follows [Semantic Versioning](https://semver.org/).

Planned work is in the [README roadmap](README.md#roadmap).

---

## [Unreleased]

---

## [0.9.0] - 2026-11-19

### Added
- Search in the Request and Response tabs: a search icon in the detail screen's top bar opens a field that finds
  text in the headers and the formatted body, ignoring case. Every match is marked, a counter shows the current
  one, and the arrows jump between them. The search runs off the main thread after typing pauses, counts at most
  5,000 matches, and the query carries over from one tab to the other. Back closes it
- A back arrow on the Requests, Mock, Stats and Settings screens that closes NetScope and returns to the app

---

## [0.8.0] - 2026-11-10

### Added
- `NetScope.notificationStyleFlow`, `TransactionRepository.observeSummary` and `Settings.lockedRedactedHeaders`
- `TransactionRepository.observeSummaries` and `getByIds`, `TransactionSummary` and `LoadTransactionsUseCase`
- Scrolling in the request list: a thin position indicator, and a pill to go back to the top that counts the
  requests that arrived while you were reading further down. The figures scroll away with the list
- `netscope-no-op` mirrors `NetScope.repository`, `settingsRepository`, `mockRuleRepository` and `mockRules`,
  with the types they use. Using them in release code needs `kotlinx-coroutines` in the app

### Changed
- Shake-to-open needs three jolts within one second, at a default threshold of 13 m/s² instead of 12, so a
  single bump, putting the phone down or walking no longer opens NetScope
- Response bodies are captured as the app reads them, so `intercept` no longer waits for the body. A body
  the app does not read stays empty, and `responseBodySize` is now the size the app read, in bytes
- Stored bodies are limited to 800 KB of UTF-8, whatever the body size limit is
- A database with no migration path is rebuilt again, as before 0.7.0; its captured traffic is lost
- p50 and p95 use the nearest rank, one position lower than before
- The notification stops reading transaction bodies
- The list and the Stats screen read no body or header: a change re-reads small rows instead of every stored
  body, and the list updates at most four times a second. `FilterTransactionsUseCase` now emits
  `TransactionSummary`, and `ComputeTransactionStatsUseCase` and `TransactionStats.slowestRequests` use it too
- Search runs in SQLite 250 ms after the last keystroke. Outside the headers it ignores the case of ASCII
  letters only, and a query is cut at 1,000 characters
- Request and response bodies wrap instead of scrolling sideways, one lazily drawn line at a time. XML shows
  each tag on its own line
- `TransactionItem` takes a `TransactionSummary`
- Each theme has its own status, method and syntax colours, all at WCAG AA contrast, so `ColorSuccess` and the
  other colour tokens, `StatusClass.color()`, `methodColor()` and `statusColor()` are now `@Composable`
- Text is at least 10 sp, tab titles share one style, and the Mock and Settings tabs lose their back arrow
- Database version 4 indexes `requestDate`; the migration keeps the stored traffic

### Fixed
- Streaming responses such as `text/event-stream` are no longer held back by the capture
- One-shot and duplex request bodies are left unread, and large ones are no longer read whole into memory
- A row too large for Android's CursorWindow no longer crashes the app; existing ones are removed on upgrade
- Large exports are shared as a file, and clipboard text over 100,000 characters is cut with a note, instead
  of crashing with `TransactionTooLargeException`
- Settings can no longer remove a header the app redacts: its chip has no remove button
- `updateConfig` lower-cases `redactedHeaders`, so a header added through it is redacted
- The mock interceptor docs give the working order: `NetScope.interceptor()` first, then
  `NetScope.mockInterceptor()`. The sample now applies its mock rules
- `NetScopeActivity` no longer crashes when Android recreates it in a new process. Until the app configures
  NetScope, it shows the saved settings over the defaults
- A second tap on Save in the mock rule editor no longer adds a duplicate rule
- Opening a large response no longer freezes the app: it was formatted on the main thread, which caused an
  ANR with a 240 KB JSON body. Formatting also no longer crashes on a body cut in the middle of an escape
- A response can no longer hang or crash the app through the body viewer: a stray `/` in an XML tag looped
  forever, and deeply nested JSON ran out of memory
- Exporting the list as HAR no longer blocks the main thread
- The request list no longer flashes "No requests yet" while it loads
- Primary tabs no longer leave a blank band above the bottom bar
- The "copied" snackbar was white on white in the light theme
- The Stats response-time percentages add up to 100%
- `toCurl` escapes single quotes in the URL and headers, so an exported command cannot break or run a value
- A mock rule with an invalid Content-Type is no longer saved, and one already saved no longer crashes the
  request
- A notification style change applies at once
- HAR files carry the library's version
- TTFB is measured from the last request after a redirect
- Stats and filtering no longer run on the main thread

---

## [0.7.5] - 2026-11-07

### Changed
- No change to the library. The publish workflow now releases the deployment on Maven Central by itself, and
  this is the first release it does so for

---

## [0.7.4] - 2026-11-07

### Fixed
- Request and response bodies are readable in the light theme: they sit on a white block with their own
  light syntax colours, and the light accent and secondary text colours reach WCAG AA contrast

---

## [0.7.3] - 2026-11-05

### Fixed
- `netscope-ui` no longer calls `FlowRow`, so apps on Compose foundation 1.8+ no longer crash on the Settings
  and mock rule editor screens

---

## [0.7.2] - 2026-11-05

### Fixed
- `netscope-ui` declares `NetScopeActivity`, so apps no longer crash when they open the inspector. An
  `<activity>` entry an app added for it can be removed

---

## [0.7.1] - 2026-11-05

### Fixed
- Apps on Kotlin 2.0 and 2.1 build against NetScope again. The modules are compiled for Kotlin 2.0 and depend
  on `kotlin-stdlib` 2.0.21; 0.7.0 carried Kotlin 2.2 metadata and `kotlin-stdlib` 2.2.10, which those
  compilers reject as "compiled with an incompatible version of Kotlin"

---

## [0.7.0] - 2026-11-02

### Added
- `NetScope.settingsRepository` and `NetScope.mockRuleRepository`, with the `SettingsRepository` and
  `MockRuleRepository` interfaces
- Use cases for mock rules, settings and transaction stats
- Unit tests for the use cases, mock rule matching and every ViewModel

### Changed
- `netscope-ui` screens follow MVI: one `uiState` and `onIntent` per flow
- The detail screen follows the transaction live, and says so when it was deleted
- The Stats response-time chart counts completed requests only
- **Breaking:** `TransactionRepository` gained `observeById`, so custom implementations must add it
- **Breaking:** the use cases moved to `io.netscope.domain.usecase.<area>`, and `SortOrder` to
  `io.netscope.model`

### Removed
- **Breaking:** `NetScopeViewModel`, `FilterState`, `MockRulesViewModel`, `NetScopeNavGraph` and the screen
  composables are no longer public
- **Breaking:** `GetTransactionsUseCase` and `GetTransactionByIdUseCase`

### Fixed
- Settings saved in the Settings screen apply when the library starts
- Network timing is kept when the response is saved
- The detail screen loads from its route argument, so it survives the app being recreated
- The notification stops updating once it is dismissed
- A database left by a newer build is rebuilt instead of crashing; its captured traffic is lost
- `netscope-no-op` has the stubs release builds need to compile the README code
- The Settings screen shows the current version

---

## [0.6.0] - 2026-05-11

### Added
- Stats screen: p50, p95 and average latency, status distribution, method breakdown, response-time
  histogram, slowest requests and top hosts
- Bottom navigation bar (Requests, Mock, Stats, Settings), hidden on the detail and editor screens

### Changed
- Design system v1.0: new colour palette, status and method colours, and redesigned list, mock rule and
  stat card components
- The transaction detail Overview and Timing tabs are redesigned
- The repository moved to the `droidnest` organisation; POM URLs now point to
  `github.com/droidnest/io.netscope`

### Fixed
- The status bar inset is no longer counted twice under the nested `Scaffold`

---

## [0.5.0] - 2026-05-02

### Added
- Mock response rules: `NetScopeMockInterceptor` returns a fixture for matching requests without calling
  the network, and the mocked responses are still recorded
- Rules match the URL by Contains, Exact or Regex, with an optional HTTP method and delay
- Screens to list, toggle, delete, create and edit rules; rules persist across app restarts
- `NetScope.mockInterceptor()`, `NetScope.mockRules` and the add, update, delete and toggle helpers

### Changed
- **Breaking:** the Maven group moved from `io.github.nidhal92` to `io.github.droidnest`

---

## [0.4.0] - 2026-04-25

### Added
- Timing tab in the transaction detail screen, with timestamps, duration and the network phase bar
- Share a transaction as HAR from the detail screen
- Export all visible transactions as HAR from the list screen
- XML syntax highlighting in request and response bodies

### Changed
- The network phase bar moved from the Overview tab to the Timing tab

---

## [0.3.1] - 2026-04-18

### Changed
- **Breaking:** `compileSdk` lowered from 36 to 35
- Dependencies lowered to versions that support `compileSdk` 35: Room 2.7.0, Navigation 2.8.9, Core KTX
  1.16.0, Lifecycle 2.8.7, Activity Compose 1.9.3 and Compose BOM 2024.12.01

---

## [0.3.0] - 2026-04-17

### Added
- `netscope-export` module: `NetScopeExporter` with cURL, HAR 1.2 and full-summary output, plus clipboard
  and share helpers
- Network phase timing (DNS, connect, TLS, TTFB) through `NetScope.timingListenerFactory()`, shown as a bar
  in the Overview tab
- Shake-to-open: `NetScopeShakeDetector`, `NetScope.registerShakeDetector` and `NetScope.setShakeEnabled`
- Settings screen with capture, privacy, notification and theme settings
- Light, dark and system themes
- `NetScope.updateConfig` applies configuration changes without a restart
- `NotificationStyle`: Off, Minimal or Detailed
- Copy buttons for each section of the detail screen, with snackbar feedback
- Consumer ProGuard rules for `netscope-ui` and `netscope-no-op`
- GitHub Actions workflow that publishes to Maven Central

### Changed
- The Maven group moved from `io.netscope` to `io.github.nidhal92`
- List and detail screens redesigned, with a stats bar and animated tab transitions
- The database moved to version 2 with a migration that adds the timing columns

---

## [0.2.0] - 2026-04-13

Date of the commit that added this work. The `v0.2.0` tag does not contain it: the tag sits on an earlier
commit than `v0.1.0`, and no build carried this version number. These changes first shipped in 0.3.0.

### Added
- `netscope-notification` module: a persistent silent notification with live request and error counts
- Filter chips for HTTP method and status class
- Sort menu: time, duration or size
- JSON syntax highlighting in request and response bodies
- `NetScopeActivity.launchIntent()` and `launchPendingIntent()`
- `:sample` demo app

### Changed
- **Breaking:** `minSdk` raised from 21 to 23

---

## [0.1.0] - 2026-03-16

### Added
- `NetScopeInterceptor`: OkHttp application interceptor
- Room database for local transaction storage
- `TransactionListScreen`: Compose UI with live search
- `TransactionDetailScreen`: tabbed Overview, Request and Response
- Header redaction (Authorization, Cookie, custom)
- Gzip response body decoding
- Auto-prune oldest transactions when the limit is exceeded
- `netscope-no-op` release artifact: zero overhead, no dependencies

[Unreleased]: https://github.com/droidnest/io.netscope/compare/v0.8.0...HEAD
[0.8.0]: https://github.com/droidnest/io.netscope/compare/v0.7.5...v0.8.0
[0.7.5]: https://github.com/droidnest/io.netscope/compare/v0.7.4...v0.7.5
[0.7.4]: https://github.com/droidnest/io.netscope/compare/v0.7.3...v0.7.4
[0.7.3]: https://github.com/droidnest/io.netscope/compare/v0.7.2...v0.7.3
[0.7.2]: https://github.com/droidnest/io.netscope/compare/v0.7.1...v0.7.2
[0.7.1]: https://github.com/droidnest/io.netscope/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/droidnest/io.netscope/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/droidnest/io.netscope/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/droidnest/io.netscope/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/droidnest/io.netscope/compare/v0.3.1...v0.4.0
[0.3.1]: https://github.com/droidnest/io.netscope/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/droidnest/io.netscope/compare/v0.1.0...v0.3.0
[0.2.0]: https://github.com/droidnest/io.netscope/commit/e9256b2
[0.1.0]: https://github.com/droidnest/io.netscope/tree/v0.1.0
