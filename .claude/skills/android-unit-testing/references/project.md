# Project profile: NetScope (Android library, `io.netscope`)

**Match check:** `settings.gradle.kts` has `rootProject.name = "NetScope"` and includes `:netscope-core`. If it does
not, this profile describes another project: rewrite the "This project" column from "How to find it" and update this
check.

| Item | How to find it | This project |
|---|---|---|
| Existing tests | `src/test`, `src/androidTest` of each module | `netscope-core` and `netscope-ui`, in `src/test/kotlin` |
| Shared testing module | A `testing` module in `settings.gradle(.kts)` | *none*. `MainDispatcherRule` lives in `netscope-ui` tests. Fakes are duplicated in `fake/CoreFakes.kt` (core) and `fake/UiFakes.kt` (ui): propose `:netscope-testing` when a third module needs them. Build a ViewModel in `@Before`, never in a field: the rule sets Main after the fields are initialised, and `viewModelScope` would bind to the wrong dispatcher |
| Test libraries in the catalog | `gradle/libs.versions.toml` | `junit` 4.13.2, `kotlinx-coroutines-test`, Turbine 1.2.0, `androidx.test.ext:junit`, `espresso-core`. **Missing**: MockK, Truth, Robolectric. Propose the additions (with versions) and wait for approval before adding them |
| Test framework | Catalog | JUnit 4 (`org.junit.Test`, `@Before`) |
| Assertions | Catalog | `kotlin.test` (comes with Kotlin) or JUnit `Assert` until a library is approved |
| What to test first | Logic without Android | `FilterTransactionsUseCase` (query, method, status and sort filters), `TransactionMapper`, `MockRule.matches` (match types, regex errors), `NetScopeExporter.toCurl` / `toHar`, then `NetScopeInterceptor` header redaction (`redactedHeaders`) and body truncation (`maxBodySize`) through OkHttp's `MockWebServer` (also missing from the catalog) |
| Fakes | Repository interfaces | `TransactionRepository` (`netscope-core/.../domain/repository/`): a fake backed by `MutableStateFlow<List<NetTransaction>>` |
| Hardcoded dispatchers | `grep -rn "Dispatchers.IO"` | `NetScopeNotificationManager` (an `object`) builds its own `CoroutineScope(SupervisorJob() + Dispatchers.IO)`. `NetScopeInterceptor` and `NetScopeTimingListenerFactory` take `ioDispatcher` as a constructor parameter |
| Room | `netscope-core/.../data/db/` | `NetScopeDatabase`, `TransactionDao`: test with an in-memory database in `androidTest`, or under Robolectric once approved |
| Test names | Team rule | `` `should … when …` `` backtick names in `src/test` |
| Run a module's tests | `./gradlew :<module>:tasks` (no product flavors) | `./gradlew :netscope-core:testDebugUnitTest` |
| Compile tests only | Same | `./gradlew :netscope-core:compileDebugUnitTestKotlin` |
