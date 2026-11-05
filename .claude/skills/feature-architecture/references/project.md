# Project profile: NetScope (Android library, `io.netscope`)

**Match check:** `settings.gradle.kts` has `rootProject.name = "NetScope"` and
`netscope-core/src/main/kotlin/io/netscope/NetScope.kt` exists. If not, this profile describes another project:
rewrite the "This project" column from "How to find it", mark a missing building block *none*, and update this check.

## Shape of the project

| Item | How to find it | This project |
|---|---|---|
| Kind | `plugins {}` of the modules, publishing setup | A **published library** (OkHttp network inspector) on Maven Central, `io.github.droidnest:<module>`; `:sample` is its demo app |
| Package root | `namespace` / packages of the modules | `io.netscope` (`io.netscope.ui`, `io.netscope.notification`, `io.netscope.export`) |
| Layers | Where `domain/`, `data/`, `ui/` live | **Across modules**: `netscope-core` holds the domain (`domain/repository`, `domain/usecase/<area>`, and the models in `model/`) and the data (`data/db`, `data/local`, `data/mapper`, `data/repository`) plus the OkHttp interceptors; `netscope-ui` holds the ui: one package per flow (`transactions`, `detail`, `settings`, `mockrules`, `mockeditor`), plus `mvi`, `component`, `navigation`, `preferences`. `notification` and `export` depend on core only |
| Feature prefix | Class names | Single feature: internal classes carry no prefix (`TransactionRepository`, `GetTransactionsUseCase`, `TransactionListScreen`); public entry points carry `NetScope` (`NetScope`, `NetScopeInterceptor`, `NetScopeActivity`, `NetScopeTheme`) |
| Screen host | Activity / Fragments / NavHost | **Navigation Compose**: `NetScopeActivity` hosts `NetScopeNavGraph`; destinations are `composable(ROUTE_LIST)`, `ROUTE_DETAIL`, `ROUTE_STATS`, `ROUTE_SETTINGS`, `ROUTE_MOCK_RULES`, `ROUTE_MOCK_ADD`, `ROUTE_MOCK_EDIT`. No Fragments |
| Wiring | DI plugins, factories | **No DI framework.** The `NetScope` object is the service locator (`NetScope.repository`, `NetScope.settingsRepository`, `NetScope.mockRuleRepository`), initialised by `NetScope.interceptor(context)`. ViewModels are built by a nested `Factory : ViewModelProvider.Factory` that creates the use cases from those repositories |
| ViewModel delegate | NavGraph | `viewModel(factory = XViewModel.Factory())`, inside the destination's `composable { }`. `TransactionsViewModel` alone is created at the NavGraph level: the list and the Stats screen are one flow |
| Reference code | The most layered flow | The transaction list: `FilterTransactionsUseCase` → `TransactionsViewModel.uiState` (`stateIn(WhileSubscribed(5_000))`) → `TransactionListScreen`; `MockRuleEditorViewModel` for a form with a backing `_uiState` |

## Role names

| Role in the skill | How to find it | This project |
|---|---|---|
| `UiState<T>` / `UiIntent` / `BaseViewModel` | Shared MVI types | `UiState<T>` (`Idle`, `Loading`, `Success(data)`, `Error(message)`, with `dataOrNull()`), `UiIntent`, `BaseViewModel`: `internal`, in `io.netscope.ui.mvi` of `netscope-ui` |
| `DataResult<T>`, `mapData`, `toUiState()` | What repositories return | *none*: all data is local (Room). Repositories return `Flow<List<…>>`, nullable values (`getById`) and `Unit`; screens fed by a `Flow` use the skill's `stateIn` rule |
| `ApiCaller` / `apiCaller.call { }` | Network call wrapper | *none*: the library intercepts the host app's calls (`NetScopeInterceptor`), it makes none |
| Retrofit, URLs, DTOs, ProGuard keep for Gson DTOs | Retrofit services | *none*. Gson is used by `netscope-core` (`TransactionEntity` columns, `MockRuleRepositoryImpl` persistence) and by `netscope-export` (HAR) |
| Room | `data/db/` | `NetScopeDatabase` (version 2, `MIGRATION_1_2`, `exportSchema = false`), `TransactionDao`, `TransactionEntity`. A schema change bumps the version and adds a `Migration`; never a destructive fallback (it would wipe the user's captured traffic) |
| `handleJobFailure(…)` | ViewModel failure helper | *none*: `viewModelScope.launch { … }` directly |
| `@IoDispatcher` / `@DefaultDispatcher` | Dispatcher qualifiers | *none*. Classes take `ioDispatcher: CoroutineDispatcher = Dispatchers.IO` as a constructor parameter (`NetScopeInterceptor`, `NetScopeTimingListenerFactory`). `NetScopeNotificationManager` is an `object`, so it has no constructor and still names `Dispatchers.IO` |
| `MainDispatcherRule` | Shared test module | *none* (see the `android-unit-testing` profile) |
| `AppTheme` | Theme composable | `NetScopeTheme(isDark)` with the `NetScopeSurface`, `NetScopeCard`, `NetScopeAccent`… accessors and `ColorSuccess` / `ColorError`… (details in the `migrate-xml-views-to-jetpack-compose` profile) |
| `AppLoader` / `AppLoadingDialog` | Loading components | *none*: no loading state today (local data) |
| `AppPopupDialog` + `AppPopup` | Dialog component | Material 3 `AlertDialog` |
| `BaseFragment` | Base Fragment | *none* (no Fragments) |
| `BaseAnalyticsHelper` / `AnalyticsEvent` | Analytics | *none*: a debugging library sends no analytics. Never add an SDK |
| Shared URL object, demo mode, shared strings | Core modules, `res/` | *none*. UI texts are hard-coded English strings in the composables (no `res/values`) |

## Rules specific to this project

- **The no-op module mirrors the public API.** `netscope-no-op` declares, in the same packages, empty versions of
  `NetScope`, `NotificationStyle`, `NetScopeNotificationManager` and `NetScopeShakeDetector`, so consumers can swap
  `debugImplementation` / `releaseImplementation`. Every public declaration added to or changed in those classes is
  mirrored there with the same signature, or release builds of consumers stop compiling.
- **Public API.** Everything a consumer does not call is `internal` (`TransactionRepositoryImpl` is); every public
  declaration has KDoc (`CONTRIBUTING.md`). Changing a public signature is a breaking change: note it in
  `CHANGELOG.md`.
- **ViewModels never call the service locator.** A ViewModel receives use cases from its `Factory`; the
  `Factory` is the only place that reads the `NetScope` repositories.
- **SDK limits.** Library modules stay on `compileSdk = 35` and `minSdk = 23`: do not add a dependency version that
  needs `compileSdk 36`.
- **Consumer ProGuard rules.** Each library module ships `consumer-rules.pro` (`consumerProguardFiles`): a class
  read by reflection (Gson models, Room entities) needs its rule there.

## Known debt

- `TransactionRepository`, the use cases and `NetScope.repository`-style accessors are public although only the
  library uses them: `netscope-ui` is another module. The no-op module cannot mirror the accessors (no domain types).
- Screens still format values and call `NetScopeExporter` themselves; they predate the rule and need UI mappers.
- `TransactionDetailScreen` and `MockRulesScreen` still use raw `Color(0x…)` values.
- `netscope-export`, `netscope-notification` and `NetScopeInterceptor` have no tests.

## Commands

| Item | How to find it | This project |
|---|---|---|
| Compile a module | `./gradlew :<module>:tasks` (no product flavors) | `./gradlew :netscope-ui:compileDebugKotlin` |
| Run a module's tests | Same | `./gradlew :netscope-core:testDebugUnitTest` |
| Full check | `CONTRIBUTING.md` | `./gradlew build` |
| Demo app | `:sample` | `./gradlew :sample:installDebug` |
| Formatter | Build plugins, IDE | No formatter or linter in the build (`kotlin.code.style=official`): format by hand as the skill says, then ask for Android Studio's **Code → Reformat Code** |
