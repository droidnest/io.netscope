---
name: feature-architecture
description: Use when creating or reviewing an Android feature module or any of its parts - a screen, ViewModel, use case, repository, gateway, DTO, mapper or analytics helper - when adding a screen that loads data, deciding where a file goes or how to name it, moving a feature's endpoints into its module, or migrating a legacy feature to MVI and Compose, even without the word "architecture".
---

# Feature Architecture

Three layers (ui / domain / data + di), MVI with one state and one intent entry point per ViewModel,
Compose screens with a thin host, use cases between the two. Apply these rules when writing code,
check them when reviewing.

## Project profile first

[references/project.md](references/project.md) binds this skill to the repository. Read it before
anything else:

- **Role names.** The rules and code below use role names: `BaseViewModel`, `UiState<T>`, `UiIntent`,
  `DataResult<T>`, `mapData { }`, `toUiState()`, `apiCaller.call { }`, `handleJobFailure(…)`,
  `@IoDispatcher`, `AppTheme`, `AppLoader`, `AppPopup`, `BaseAnalyticsHelper`, `AnalyticsEvent`… The
  profile gives the project's class for each. Write the project's class, never the role name.
- **The shape of the project**: how screens are hosted (Fragments or Navigation Compose
  destinations), how objects are wired (Hilt or by hand), whether layers are packages of one feature
  module or separate modules, the reference feature to read for real code, the legacy APIs new code
  must not use, the project-only chores of a migration, and the build, test and format commands.

Its match check names the repository it describes. When the check fails in the repository you are
in, the profile belongs to another project: rewrite `project.md` in place (git keeps the old one),
filling the "This project" column from "How to find it" and updating the match check, then show the
user what you found and continue. A role the project lacks is marked *none*: propose the building
block (in the shared UI or core module) and wait for approval rather than inventing it inside a
feature.

**Names derived from a role follow the project's class.** When the project's `UiIntent` is `Action`
and its `BaseViewModel` entry point is `dispatch(action)`, intents are `<Feature><Flow>Action`, the
ViewModel overrides `dispatch`, and the screen takes `onAction`; when its `UiState` is exposed as
`state`, so is yours. Follow the reference feature wherever the skill's names and the project's base
classes disagree.

Full code for each workflow step, with a fictional `Wallet` feature: [references/templates.md](references/templates.md).

## The layers

```
ui  ──calls──►  domain  ◄──implements──  data          di: binds data → domain
```

| Layer    | Contains                                                               | Must NOT contain                                   |
|----------|------------------------------------------------------------------------|----------------------------------------------------|
| `ui`     | Screen hosts, Compose screens, ViewModels, `UiIntent`, screen `UiState`, UI models, UI mappers | API calls, DTOs, repositories, gateways |
| `domain` | Use cases, models, repository interfaces, gateway interfaces, analytics interfaces | `android.*`, `Context`, `NavController`, any `ViewModel` (the profile's legacy ones included), `LiveData`, Retrofit, Gson, Parcelize, Firebase |
| `data`   | Repository / gateway / analytics implementations, Retrofit service, URLs, DTOs, data mappers, local sources | anything from `ui`: any `ViewModel` (legacy ones included), `Fragment`, `NavController`; any use case |
| `di`     | With Hilt: `<Feature>Module` (`@Provides` the service), `<Feature>BindsModule` (`@Binds` every Impl → interface). Without a DI framework: the factory or service locator the profile names | logic |

```
<package-root>.<feature>
├── ui/
│   ├── common/model/, common/mapper/,    shared by several flows, never imports a flow package
│   │   common/component/                 shared composables (WalletErrorSheet)
│   └── <flow>/                           <Feature><Flow>ViewModel, …UiIntent, …UiState
│       ├── model/, mapper/
│       └── <screen>/                     <Feature><Screen>Fragment (or route) + <Feature><Screen>Screen, sheets
│                                         (a flow with a single screen keeps them in <flow>/)
├── domain/
│   ├── model/, repository/, gateway/, analytics/
│   └── usecase/<area>/
├── data/
│   ├── remote/ (Service, Urls), remote/models/ (DTOs), local/, mapper/, repository/, analytics/
│   └── <capability>/                     gateway implementations named by technology (qr/, image/)
└── di/
```

**When the layers are modules**, not packages (a library split into `core` and `ui` modules, say:
profile), the same rules hold between the modules: the UI module depends on the domain API of the
core module and never on its data implementations, which stay `internal`.

**Dependencies only point down.** A repository (or gateway) implementation depends only on data
sources: Retrofit service, `ApiCaller`, DAO or local repository, preferences, system services
through the `Context`, another repository. It never injects a use case, of its own feature or of
another one, and never a `ViewModel` or a `Fragment`: `data` must not depend on `ui`, even through a
legacy ViewModel that holds the value it needs. Read the source that ViewModel reads instead (the
profile lists the legacy ViewModels and their sources). Combining data, or using another feature's
data, is business logic: a use case does it, calling the repositories it needs (or another feature's
use case).

`NavController` never goes below the `ui` layer. A repository calls `apiCaller.call { service.x() }`
and never hands it a `NavController`: session expiry is raised by the network layer and handled by
the host Activity, not by the caller (the profile names any legacy overload that takes one).

A Retrofit call answering an empty or meaningless body (`Response<ResponseBody>`) never lets
`ResponseBody` leave `data`: the repository returns `DataResult<Unit>` with `.mapData { }`. An
endpoint whose query parameter the legacy code always sent as `null` loses that parameter: Retrofit
drops `null` queries, so the request is the same.

Every DTO in `data/remote/models/` is a top-level class in its own file, and its name ends in `Api`.
Never nest a DTO inside another one (`WalletHistoryListResponseApi.Item`): a JSON object inside a
response becomes its own `<Feature><Concept>Api` file, referenced by the parent's field. When
migrating a legacy model with inner classes, flatten them this way; the `@SerializedName` keys stay
the same, so the JSON does not change.

## Wiring

- **With Hilt**, every class takes its dependencies through an `@Inject constructor`, the `di`
  modules bind each implementation to its interface, and ViewModels are `@HiltViewModel`.
- **Without a DI framework** (profile), classes keep the same constructor shape (dependencies as
  constructor parameters, never looked up inside the class) and the project's factory builds them:
  a `ViewModelProvider.Factory`, a service locator, a library entry object. New wiring goes where the
  existing wiring is, and a ViewModel still receives use cases, never the locator itself.

## Use cases

- One class, one action, one `operator fun invoke(...)` (`suspend` when it does I/O). Called as
  `useCase(args)`, never `execute()`.
- Failures are values: return `DataResult<T>` or a sealed result (`WalletPreparePaymentResult`),
  never throw. A use case that observes local data returns a `Flow`.
- A use case depends only on domain types: repository, gateway and analytics **interfaces**, other
  use cases, domain models. Never on the Android framework: no `Context`, `NavController`,
  `ViewModel` (the legacy session ViewModels of the profile included: use the repository the profile
  names), `LiveData`, `Fragment` or other `android.*` type, not even as a parameter. A value only
  Android knows (a biometric capability, a string) comes through a gateway interface implemented in
  `data`.
- **One class per file**, named after the class, even when the use cases are small.
- **Group them by business area** once the feature has more than a handful, or more than one flow:
  `domain/usecase/<area>/`, area in lowercase (`account`, `payment`, `qrcode`, `history`), `common/`
  for use cases several areas share: `usecase/payment/WalletCheckLimitsUseCase`,
  `usecase/qrcode/WalletParseQrUseCase`, `usecase/common/WalletIsDarkModeEnabledUseCase`. A feature
  with two or three use cases keeps them flat in `domain/usecase/`.
- **Another feature's legacy use case** can be called from a use case as it is; adapt its legacy
  signature inside the calling use case (the profile says how: a `NavController` parameter to pass as
  `null`, a `Flow` whose single result is taken with `.first()`…).
- **A value held by a legacy `ViewModel`** is read neither by the use case nor by a repository
  through that ViewModel. The repository or gateway reads the ViewModel's own source (a
  configuration flag, a preference: the profile lists the known ones). When the ViewModel also
  combines data, split it: the gateway reports what the device knows (a fingerprint sensor and its
  enrolment), the use case adds the rest (the saved user turned it on).

## Coroutine dispatchers

A class never names `Dispatchers.IO` or `Dispatchers.Default`: it takes the dispatcher as a
constructor parameter, qualified by `@IoDispatcher` (blocking I/O: Room DAOs that are not `suspend`,
files, JSON assets) or `@DefaultDispatcher` (CPU-bound work: parsing, sorting, mapping large lists).
The profile says where both qualifiers and their bindings live and which module to depend on; without
a DI framework, the factory passes the dispatcher in, with `Dispatchers.IO` as the default value of
the constructor parameter at most.

```kotlin
internal class LoanHistoryRepositoryImpl @Inject constructor(
    private val dao: LoanHistoryDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : LoanHistoryRepository {

    override suspend fun getHistory(): DataResult<List<LoanHistoryItem>> = withContext(context = ioDispatcher) {
        // …
    }
}
```

- The switch happens where the blocking call is, in `data`: the repository or local source, never
  the use case or the ViewModel. A Retrofit `suspend` call through `apiCaller.call { … }` and a Room
  `suspend` DAO need no switch.
- ViewModels launch on `viewModelScope` without a dispatcher; tests replace Main with the shared
  `MainDispatcherRule` (profile).
- Tests pass a `TestDispatcher` (`StandardTestDispatcher(scheduler = testScheduler)`) in place of
  the injected one.

## MVI: mandatory building blocks

| Block           | Rule |
|-----------------|------|
| `UiState<T>`    | Shared screen-state wrapper. Each ViewModel exposes exactly one `val uiState: StateFlow<UiState<T>>`, backed by a private `_uiState`. |
| `UiIntent`      | Marker interface. Each flow declares `internal sealed interface <Feature><Flow>UiIntent : UiIntent`. |
| `BaseViewModel` | Each ViewModel extends it and overrides `onIntent(intent: UiIntent)`, starting with `if (intent !is <Feature><Flow>UiIntent) return`. |

`UiState<T>` cases: `Idle`, `Loading`, `Success(data)`, `Error(message: String?, @StringRes defaultMessage)`.

A screen fed by a local `Flow` that never fails (a database observed live) may skip `Loading` and
`Error`: its `T` is the content, built with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), …)`
from the use case's `Flow` combined with the screen's own inputs (query, filters).

### Turning results into state: `toUiState()`

A use case result becomes state **only** through `DataResult<T>.toUiState()`. Convert the data first
with `mapData`, then call `toUiState()`; never build `UiState.Success` / `UiState.Error` by hand from
a `DataResult`. A failed intermediate step whose type is not the screen's (`DataResult<Unit>` while
the state is `UiState<WalletPaymentUiState>`) goes through `mapData` too:
`result.mapData { content }.toUiState()`.

```kotlin
_uiState.value = getHistorySectionsUseCase()
    .mapData { WalletHistoryUiState.Content(sections = historyUiMapper.toSections(sections = it.orEmpty())) }
    .toUiState()
```

`toUiState()` maps a success (an empty body becomes an error), an error (server message, then the
error type's message), a loading, and a closed session (→ `Idle`); the profile gives the exact cases
of the project's type. A crash caught in `runCatching` goes the same way:
`DataResult.Error(exception = failure as? Exception).toUiState()`.

Build a `UiState` by hand only when there is no `DataResult`:

- a business error: `UiState.Error(defaultMessage = R.string.…)`; when the message needs a value,
  resolve the text in an injected UI error mapper that holds the `Context` and pass it as `message`
  (`WalletLimitUiErrorMapper`);
- a use case that returns a plain value (`WalletGetTermsUseCase` → `String?`);
- a screen that shows a form first and starts in `UiState.Success(…)`.

### Choosing `T`

| The screen shows…                                | `T` is                                                                  | Example                    |
|--------------------------------------------------|-------------------------------------------------------------------------|----------------------------|
| one value                                        | that value                                                              | `UiState<String>` (terms)  |
| several things, and navigates or shares once     | a sealed class `<Feature><Flow>UiState` with `Content` and `Navigating` | `WalletHistoryUiState`     |

The screen `UiState` class holds the content in every case, and `Navigating` adds where to go
(`openedItem`, or a `<Feature><Flow>Destination`). The screen navigates, then sends an ack intent
(`NavigationHandled`, `DetailsOpened`) and the ViewModel goes back to `Content`.

- **Open overlays are content.** A picker or menu sheet, or a confirmation asked before an action,
  is a field of `Content` (`isPickerOpen`, `picker: <Feature><Flow>Picker?`, `itemToDelete`), set and
  cleared by intents (`PickerClicked`, `PickerDismissed`, `DeleteCancelled`).
- **A done action is a state.** After a save, the content becomes `Saved` (the screen shows the
  success popup); its ack `SavedConfirmed` moves it to `Navigating`.
- **Errors.** After an `Error` is shown, the screen sends `ErrorShown`. A screen that had nothing
  loaded goes back to `Idle`. A screen whose action failed (a form, a list being changed) gives the
  user's content back: the ViewModel keeps it in a private `contentBeforeSave` (or
  `formBeforeValidation`…) when the action starts and restores `Success(it)` on `ErrorShown`; reset
  it when a fresh load starts, so a stale copy never comes back.

### ViewModel, screen and host rules

The **host** is what owns the ViewModel and navigates: a Fragment returning a `ComposeView`, or,
when the project navigates with Navigation Compose (profile), the destination's route composable
inside `composable(route) { … }`. Every rule below that names the Fragment applies to the route
composable in that case.

- The ViewModel's only public members are `uiState` and `onIntent`.
- The ViewModel injects **use cases only** (plus the feature's analytics interface and its UI
  mappers): never a repository, a gateway, a Retrofit service, a DAO, a session repository, a service
  locator or a legacy session ViewModel. When it needs a repository, create a use case that calls
  it, even a one-line pass-through, and inject that use case instead.
- No `SharedFlow` / `Channel` / `LiveData` of single-consumption events in new ViewModels:
  navigation and errors are state.
- **The screen is Compose**, in `AppTheme`. A Fragment host returns a `ComposeView`
  (`DisposeOnViewTreeLifecycleDestroyed`; when the base Fragment pads a child of the root with the
  insets, override its hook to pad the root: profile). A route host gets the ViewModel with the
  project's delegate (`hiltViewModel()`, `viewModel(factory = …)`). Either way the host collects the
  state with `viewModel.uiState.collectAsStateWithLifecycle().value` and passes `uiState`,
  `onIntent = viewModel::onIntent` and one callback per navigation (`onOpenDetails`, `onBackClick`)
  to `<Feature><Screen>Screen`. The screen composable never receives the ViewModel or a `Flow`.
- **The composable draws everything the state says**: the loading (`AppLoader` for a first load,
  `AppLoadingDialog` over content), the errors and confirmations (`AppPopupDialog` + `AppPopup`, or a
  feature sheet such as `WalletErrorSheet`), and sends the ack intent (`ErrorShown`,
  `SavedConfirmed`). It reacts to `Navigating` in a `LaunchedEffect` keyed on the destination,
  calling the host's callback. Never the legacy popup, loader or loading helpers the profile lists,
  not even in a Compose screen's host: the host only navigates.
- **Keep the content drawn under the loader and popups.** `Loading` and `Error` carry no content, so
  a form or a list would vanish while saving. The composable remembers the last `Success` data
  (`uiState.dataOrNull()?.let { content = it }`) and draws `AppLoadingDialog` / the error over it.
- Intents name **what happened** (`Started`, `ItemClicked`, `AmountTyped`), never what to do.
- Coroutine failures go through `handleJobFailure`; `CancellationException` is rethrown.
- One ViewModel per flow: scoped to the flow's navigation graph when the flow spans screens (the
  profile gives the delegate), scoped to the screen otherwise. A screen with nothing to load nor
  decide (an end-of-flow status) has no ViewModel: its host shows the composable and navigates from
  its button.
- **`Started` is sent once per ViewModel, before the screen composes.** A Fragment host sends it from
  `onCreateView`, before returning the `ComposeView` (it composes as soon as it is attached, before
  `onViewCreated`), when `savedInstanceState == null`; a route host sends it from
  `LaunchedEffect(Unit)`. Navigation arguments reach the ViewModel through it
  (`Started(contact = args.contact)`). A ViewModel scoped to the graph, and any ViewModel of a route
  host (its `LaunchedEffect` runs again when the user comes back to the destination), ignores
  `Started` once it has started (`if (_uiState.value != UiState.Idle) return`), so coming back to the
  screen does not reload it.
- **A legacy component that needs a Fragment** (a biometric prompt helper that takes the `Fragment`)
  is the one exception to "the host only navigates": the ViewModel asks for it with a destination
  (`Navigating(destination = BiometricPrompt)`), the Fragment runs it and sends what happened back as
  intents (`BiometryChecked`, `BiometryFailed`). Observe the legacy `LiveData` in `onCreate` with the
  Fragment itself as owner, not `viewLifecycleOwner`, so a sticky value is not sent again when the
  view is recreated. Say so in the Fragment's KDoc.
- Shared composables of a feature live in `ui/common/component/` (`WalletErrorSheet`), pickers used
  by several screens included.

## UI model or domain model

Create a `<Feature><Concept>Ui` (built in a UI mapper) only when the screen needs formatted values
(`WalletHistoryItemUi.dateText`), Android types (`WalletDetailRowUi.valueColor`), a navigation
argument (`@Parcelize`), extra fields (`WalletAmountInputUi.isValid`) or a grouping
(`WalletHistorySectionUi`). Otherwise keep the domain model (`WalletPaymentRequestUi.beneficiary:
WalletBeneficiary`). Formatting never happens in a host or a composable. A mapper that needs a
`Context` is an injected class (`WalletHistoryUiMapper`), so the ViewModel never holds one.

A model passed as a navigation argument is as small as possible. Prefer plain arguments
(`contactId: string`, `setAsDefault: boolean`) to a model, and a boolean to a legacy menu enum.

## Analytics helpers

Analytics is a port like a repository: the domain says *what* happened, the data layer says *how* it
is sent.

| Piece          | Layer / package      | Name                            | Shape |
|----------------|----------------------|---------------------------------|-------|
| Interface      | `domain/analytics/`  | `<Feature>AnalyticsHelper`      | one `fun log…()` per business event, pure Kotlin types only |
| Implementation | `data/analytics/`    | `<Feature>AnalyticsHelperImpl`  | `@Inject constructor() : BaseAnalyticsHelper(), <Feature>AnalyticsHelper`; maps each call to an `AnalyticsEvent` with the base class's log functions |
| Binding        | `di/`                | in `<Feature>BindsModule`       | `@Binds abstract fun bind<Feature>AnalyticsHelper(impl: <Feature>AnalyticsHelperImpl): <Feature>AnalyticsHelper` |
| Caller         | `ui/<flow>/`         | the ViewModel                   | constructor-injects the **interface** (`analyticsHelper: <Feature>AnalyticsHelper`) and logs in the `onIntent` branch of the intent that triggered the event |

- The implementation extends `BaseAnalyticsHelper`, which needs `Context` and the analytics SDKs, so
  it can never sit in `domain`.
- Hosts and composables do not log: the intent the screen already sends is where the ViewModel logs.
- New events are added to the project's `AnalyticsEvent` type when the base class only takes that
  type (profile).
- Features that predate this keep their legacy analytics shape (profile). When touching one, move
  the implementation to `data/analytics/…Impl`, bind it, and inject the interface into the ViewModel.
- A project without analytics (profile: *none*) skips this section; do not add an SDK for one screen.

## Naming

Formula: `<Feature>` + what it is + its role: `Wallet` `GetHistorySections` `UseCase`. A
single-feature project (a library) may drop the prefix where the profile says so.

| Kind                     | Pattern                                             | Example                                    |
|--------------------------|-----------------------------------------------------|--------------------------------------------|
| Screen host / dialog     | `<Feature><Screen>Fragment` / `<Feature><Name>Dialog`; a route constant `ROUTE_<SCREEN>` for Navigation Compose | `WalletScanFragment`, `WalletTermsDialog` |
| Compose screen / sheet   | `<Feature><Screen>Screen` / `<Feature><Name>Sheet`  | `WalletHistoryScreen`, `WalletAccountDetailsSheet` |
| ViewModel                | `<Feature><Flow>ViewModel`                          | `WalletHistoryViewModel`                   |
| Intents                  | `<Feature><Flow>UiIntent`                           | `WalletHistoryUiIntent`                    |
| Screen state (`T`)       | `<Feature><Flow>UiState` (`Content` / `Navigating`) | `WalletHistoryUiState`                     |
| Destinations             | `<Feature><Flow>Destination`                        | `WalletPaymentDestination`                 |
| UI model                 | `<Feature><Concept>Ui`                              | `WalletHistoryItemUi`                      |
| UI mapper                | `<Feature><Concept>UiMappers.kt` (functions) or `<Feature><Concept>UiMapper` (injected, needs `Context`) | `WalletAccountUiMappers.kt`, `WalletHistoryUiMapper` |
| Use case                 | `<Feature><Verb><Noun>UseCase` in `usecase/<area>/` | `payment/WalletCheckLimitsUseCase`         |
| Domain model             | `<Feature><Concept>`                                | `WalletLimits`                             |
| Repository / impl        | `<Feature><Concept>Repository` / `…RepositoryImpl`  | `WalletTermsRepository(Impl)`              |
| Gateway / impl           | `<Feature><Role>` / `<Feature><Technology><Role>`   | `WalletQrPayloadParser` / `WalletEmvcoQrPayloadParser` |
| Analytics / impl         | `<Feature>AnalyticsHelper` / `<Feature>AnalyticsHelperImpl` | `WalletAnalyticsHelper`            |
| Retrofit service         | `<Feature>Service`                                  | `WalletService`                            |
| URLs                     | `<Feature>Urls` in `data/remote/<Feature>Urls.kt`   | `WalletUrls.HISTORY`                       |
| DTO                      | `<Feature><Concept>RequestApi` / `ResponseApi`      | `WalletInitRequestApi`                     |
| Nested JSON object (DTO) | `<Feature><Concept>Api`, top-level, own file        | `WalletHistoryEntryApi`                    |
| Room entity / DAO        | `<Concept>Entity` / `<Concept>Dao` in `data/db/`    | `TransactionEntity`, `TransactionDao`      |
| Data mappers             | `<Feature><Concept>Mappers.kt` (`toDomain()`, `to<X>Api()`, `toEntity()`) | `WalletLimitsMappers.kt` |
| Hilt modules             | `<Feature>Module`, `<Feature>BindsModule`; `<Feature>HiltModule` while a legacy Dagger `<Feature>Module` still exists | `WalletModule`, `WalletBindsModule` |
| Test                     | `<ClassName>Test`, `` `should … when …` ``           | `WalletHistoryViewModelTest`               |

The feature's URLs live in its module (`<Feature>Urls`), never in the project's shared URL object
(profile). Demo or mock files follow the project's demo mechanism (profile).

Strings only this feature uses go in its own `res/values/strings.xml`, with its translations; reuse
an existing shared string (profile) before adding one.

Resources carry the feature after the type, in snake_case: `<feature>_<name>.xml` for drawables,
`<feature>_navigation.xml`; nav ids `<feature><Screen>Fragment` (`walletHistoryFragment`). A legacy
screen not yet in Compose keeps `fragment_<feature>_<screen>.xml`, `dialog_<feature>_<name>.xml`,
`adapter_<feature>_<name>.xml`.

Injected dependencies are named after their type in lowerCamelCase without the feature prefix
(`getHistorySectionsUseCase: WalletGetHistorySectionsUseCase`, `repository: WalletRepository`,
`analyticsHelper: WalletAnalyticsHelper`); qualified dispatchers keep their qualifier
(`@IoDispatcher ioDispatcher`).

**Every call names its arguments** in new code, production and tests:
`WalletHistoryEntry(reference = reference, label = label)`, `repository.saveOffers(offers = offers)`,
`onIntent(intent = WalletHistoryUiIntent.Started)`, `stringResource(id = R.string.wallet_title)`,
`Modifier.padding(all = 16.dp)`, `assertEquals(expected = expected, actual = result)`. The only
exceptions are where Kotlin forbids names or they add nothing: Java methods (`getString`,
`setLayout`, `super.onViewCreated(view, savedInstanceState)`), varargs (`listOf(a, b)`, `key(id)`), a
lone trailing lambda (`map { … }`) and calls of a function-type value (`onClick()`, `onIntent(…)`
passed as a lambda parameter). Code that predates the rule is fixed when touched, not in a sweep.

A **published library** (profile) also keeps its public API deliberate: everything a consumer does
not call is `internal`, and every public declaration has KDoc.

## Workflow: add a screen that loads data

Build inside out; each step compiles before the next. Templates in
[references/templates.md](references/templates.md); real code in the profile's reference feature.

1. **DTO + endpoint (data):** `…ResponseApi`, the URL in `data/remote/<Feature>Urls.kt`, a method on
   `<Feature>Service` with its demo file when the project has a demo mode. The first DTO of
   `data/remote/models/` needs its keep rule in the feature's `proguard-rules.pro` (see "Migrating a
   legacy feature"). For local data, the entity and DAO instead.
2. **Domain model + repository interface (domain).**
3. **Repository implementation (data):** `toDomain()` mapper,
   `apiCaller.call { service.x() }.mapData { it.toDomain() }`.
4. **Wiring (di):** `@Binds` the Impl in `<Feature>BindsModule`, or add it to the project's factory.
5. **Use case (domain):** in `usecase/<area>/` when the feature groups them.
6. **Analytics, if the screen reports events:** interface in `domain/analytics`, Impl in
   `data/analytics`, `@Binds`.
7. **Intent, screen `UiState`, UI models (ui).**
8. **ViewModel (ui):** extends `BaseViewModel`, overrides `onIntent`, `mapData { … }.toUiState()`,
   logs analytics in `onIntent`.
9. **Compose screen + host (ui):** the composable draws content, loader and popups; the host
   (Fragment with a `ComposeView`, or route composable) owns the ViewModel and navigates. No layout
   file.

Then write the mapper, use case and ViewModel tests (use the `android-unit-testing` skill).

Finish with a code cleanup of every Kotlin file you touched:

- **Remove unused imports.** Check that each import is used in the file. Keep the imports the
  compiler uses without the name appearing in the code: `getValue` / `setValue` behind a `by`
  delegate, and other `operator` imports.
- **Reformat** with the profile's formatter command. When it cannot run (the IDE holds a lock),
  fix the format by hand: collapse two or more blank lines into one, remove blank lines right before
  a `}`, remove trailing spaces, and end the file with a newline. Then tell the user to run the IDE's
  reformat with "Optimize imports" ticked.
- **Compile** the touched modules with the profile's variant tasks (main and unit-test sources when
  tests changed).

**A new screen in a module still on Dagger** is Hilt from the start, in a hybrid module: apply the Hilt
plugin, keep the new Fragment out of the legacy component's `inject()` methods, name the new Hilt
module `<Feature>HiltModule` next to the legacy `<Feature>Module`, put new code in the skill's packages
even where the legacy ones differ (`usecases/`, `models/`), and follow the `android-dagger-to-hilt`
skill for the hybrid graph (its `@InstallIn` escape hatch and its migration ledger).

## Migrating a legacy feature

A dedicated `refactor(<module>)` change, screen by screen, inside out as in the workflow above. Use
the `android-dagger-to-hilt` skill for a feature still on Dagger, the `live-data-migration` skill for
ViewModels still on LiveData, and the `migrate-xml-views-to-jetpack-compose` skill for the visual
parity of each layout. Before writing code, read every legacy screen, ViewModel, use case and layout
of the module, and list:

- **Behaviors that look like bugs** (a success branch that does nothing, an analytics event logged
  in only one branch, a parameter always sent as `null`). Ask the user whether to keep or fix each
  one; when kept, say it in a comment (`// As the legacy screen: …`) and cover it with a test.
- **Legacy dependencies the new ViewModel may not use** (the profile's legacy ViewModels, another
  feature's ViewModel, a service locator called from the ViewModel): replace each with a use case
  calling the underlying use case or repository.

Then, besides the new files:

- Delete the legacy package, the adapters, the XML layouts and the example tests (`git rm`).
- Rewrite `res/navigation/<feature>_navigation.xml` (or the navigation graph builder): new class
  names, `<feature><Screen>Fragment` ids, plain arguments; keep the deep links and the graph id the
  app includes.
- Move the feature's URLs to `<Feature>Urls` (leave in the shared URL object what another module
  still uses).
- Update `proguard-rules.pro`. When the module's rules are **consumer** rules (applied in the app's
  R8 pass of minified release builds, so a missing rule only breaks there), keep the DTO package,
  whose fields Gson reads by reflection (`-keep class <package-root>.<feature>.data.remote.models.** { *; }`),
  and the name of each navigation-argument model, which the nav graph names in `app:argType`
  (`-keepnames class <package-root>.<feature>.ui.<flow>.model.<Feature><Concept>Ui`, with the package
  the model actually lives in). Remove the rules of the deleted packages.
- `build.gradle.kts`: add the Compose plugin and the shared modules a migrated feature needs, as the
  reference feature does (profile); remove the modules nothing imports any more.
- Tests go in `src/test/java/<package>/` (or `src/test/kotlin/`), fakes in `fake/<Feature>Fakes.kt`.
  ViewModel tests use the shared `MainDispatcherRule`: never copy it into the module. Deleting the
  only example test deletes the folder: create it again (`mkdir -p`) before writing, and check that
  no file landed at the repository root.
- Do the project-only chores the profile lists (analytics screen registry, layering test…).
- Compile the module **and the app module** (it references the screens), and run the module's unit
  tests.

## Review checklist

- [ ] Each file is in its layer and package (or module); `domain` has no Android, `Context`, `NavController`, `ViewModel` or analytics-SDK import.
- [ ] Repositories and gateways depend on data sources only: no use case, no `ViewModel` (legacy ones included), no `Fragment`.
- [ ] Use cases expose `operator fun invoke`, one per file, and are grouped in `usecase/<area>/` when the feature has several areas.
- [ ] The ViewModel extends `BaseViewModel`, overrides `onIntent` with the `!is` guard, uses only use cases, UI mappers and analytics interfaces, all passed through its constructor.
- [ ] No repository, gateway, service, session object or service locator used by a ViewModel: each one is wrapped in a use case.
- [ ] Use case results reach the state through `mapData { … }.toUiState()`; hand-built `UiState` only for the three cases above.
- [ ] Intents implement `UiIntent`; one `StateFlow<UiState<T>>`; navigation lives in `Navigating`, acknowledged by an intent.
- [ ] No `SharedFlow` / `Channel` / event-wrapper LiveData; the host never reads `uiState.value`, calls a use case or logs analytics.
- [ ] The screen is Compose in `AppTheme`; loading, errors and confirmations are drawn in Compose (`AppLoader`, `AppLoadingDialog`, `AppPopupDialog`); none of the profile's legacy popup or loader helpers.
- [ ] Forms and lists keep their content under the loader and give it back on `ErrorShown` after a failed action; overlays and confirmations are fields of the state.
- [ ] `Started` is sent once, before the screen composes; navigation arguments reach the ViewModel through it.
- [ ] Repositories call `apiCaller.call { … }` without a `NavController` and return domain models.
- [ ] No `Dispatchers.IO` / `Dispatchers.Default` named inside production classes: the dispatcher comes through the constructor.
- [ ] Analytics: interface in `domain/analytics`, `…Impl` in `data/analytics`, bound in `<Feature>BindsModule`, injected as the interface.
- [ ] UI models exist only when a rule above asks; formatting lives in UI mappers.
- [ ] The feature's URLs are in `<Feature>Urls`, its strings in its own `res/values*`.
- [ ] DTOs are top-level classes, one per file, all suffixed `Api`; no nested class in `data/remote/models/`.
- [ ] `proguard-rules.pro` keeps the DTO package and the navigation-argument models, and names no deleted package.
- [ ] Names follow the table; dependencies are named after their type.
- [ ] Every call names its arguments (except Java methods, varargs, lone lambdas and function-type values).
- [ ] A published library exposes only what consumers call, with KDoc.
- [ ] No unused imports; touched files are reformatted (no double blank lines, no blank line before `}`, final newline).
- [ ] Tests exist, named `should … when …`; the profile's layering test, when the project has one, passes.
