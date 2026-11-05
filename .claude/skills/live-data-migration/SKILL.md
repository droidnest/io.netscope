---
name: live-data-migration
description: 'Use when migrating one Android ViewModel off androidx LiveData - converting its MutableLiveData properties (state, single-consumption event wrappers, result types) to StateFlow or Channel, and rewriting every Fragment, Activity and Dialog observe() call site that reads them, in whichever module they live.'
---

# LiveData Migration

## Overview

Convert **one ViewModel at a time** off `androidx.lifecycle.LiveData`. The unit of work is a single
ViewModel class: all of its LiveData properties, and every call site that reads them, wherever
those call sites live. Other ViewModels, including the ones observed by the same Fragment, stay on
LiveData until their own turn.

The migration is mechanical, but two properties of a typical LiveData codebase make naive conversion
silently break behaviour:

1. **Much of the LiveData carries one-shot events, not state.** Legacy code wraps them in a
   single-consumption wrapper (`Event<T>` below: `MutableLiveData<Event<T>>`, read with
   `getContentIfNotHandled()`), which exists *only* to stop LiveData replaying its last value.
   Converting those to `StateFlow` re-fires navigation and dialogs every time a Fragment returns
   from the back stack.
2. **Almost nothing has an initial value.** Most declarations use the empty constructor
   `MutableLiveData<T>()`. `StateFlow` requires an initial value, so the target type is
   `StateFlow<T?>` with `null`; nullability does **not** go away.

## Project profile

Read [references/project.md](references/project.md) first. It maps the role names used below to this
repository's classes (`Event<T>` → the project's event wrapper, `DataResult<T>` → its result type, the
job-failure helper, the legacy `…LD` suffix), lists the codebase facts that change the migration (Compose,
linters, test infrastructure, dispatchers), and gives the compile tasks. Write the project's names, never the
role names. Its match check names the repository it describes: when the check fails in the repository you are
in, rewrite `project.md` in place (git keeps the old one), filling the "This project" column from "How to
find it" (it is mostly greps) and updating the match check, show the user what you found, then start. When the
profile says the project has no LiveData, there is nothing to migrate: say so and stop.

## Scope: one ViewModel, every call site

A ViewModel's properties are rarely read only from its own module. A ViewModel in a shared module
is often observed from several feature modules, so a single-ViewModel migration routinely touches
three or four modules. That is expected; do not shrink the change to one module and leave a call
site broken.

Conversely, do not widen it. When a Fragment observes this ViewModel *and* others, only the lines
for this ViewModel change. Converting a neighbour "while you are in the file" turns a reviewable
diff into a module migration.

### Finding the call sites

Some ViewModels are not obtained by the Fragment directly: they are field-injected into *another*
ViewModel (`@Inject lateinit var saveUserViewModel: SaveUserViewModel` inside a host ViewModel),
and the Fragment reaches them through the host, often inside an `apply` block:

```kotlin
getProspectViewModel.apply {
    getProspectLD.observe(viewLifecycleOwner) { handleProspect(it) }
    saveUserViewModel.userSavingLD.observe(viewLifecycleOwner) { handleSavedUserLD(it) }
}
```

Grepping for the class name only finds the hosts, not the observers. Grep for **each property
name** across the whole repo, `src/test` included:

```bash
grep -rn "fooLD\|barLD" --include='*.kt' . | grep -v /build/
```

A host that only calls the ViewModel's methods (`saveUserViewModel.saveNewUser(…)`) needs no change.

## Renaming rule

The suffix follows the **target type**, so the name tells a reader how the property behaves without
opening the ViewModel:

| Target                                          | Suffix  | Example                                      |
|-------------------------------------------------|---------|----------------------------------------------|
| `StateFlow<T?>` (state)                         | `Flow`  | `_menusFlow` / `menusFlow`                   |
| `Channel` exposed as `Flow<T>` (one-shot event) | `Event` | `_updateProfileEvent` / `updateProfileEvent` |

The legacy suffix (the profile names it, `LD` in the examples) does not survive the migration.
Rename the property, its backing field, every call site, and the Fragment handler that consumed it
(`handleSavedUserLD(event: Event<…>?)` becomes `handleSavedUser(response: …)`) in the same commit.
A half-renamed ViewModel is worse than an unrenamed one: `fooLD` sitting next to `fooFlow` reads as
two different properties. Other ViewModels keep their legacy names; the rule applies to the one
being migrated.

## Target patterns

| Before                                                                                                                  | After                                                                                                                                                         |
|-------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `private val _fooLD = MutableLiveData<Event<DataResult<T>>>()`<br>`val fooLD: LiveData<Event<DataResult<T>>> = _fooLD`  | `private val _fooEvent = Channel<DataResult<T>>(Channel.BUFFERED)`<br>`val fooEvent: Flow<DataResult<T>> = _fooEvent.receiveAsFlow()`. **Drop the wrapper** |
| `private val _fooLD = MutableLiveData<DataResult<T>>()`<br>`val fooLD: LiveData<DataResult<T>> = _fooLD`                | `private val _fooFlow = MutableStateFlow<DataResult<T>?>(null)`<br>`val fooFlow: StateFlow<DataResult<T>?> = _fooFlow.asStateFlow()`                          |
| `val fooLD: MutableLiveData<T> = _fooLD` (public mutable)                                                               | Expose read-only: `val fooFlow: StateFlow<T?>` / `val fooEvent: Flow<T>`. Never expose `MutableStateFlow` or `Channel`.                                       |
| `_fooLD.postValue(x)` / `_fooLD.value = x` (StateFlow target)                                                           | `_fooFlow.update { x }` (`kotlinx.coroutines.flow.update`): atomic and safe from any thread; `postValue` has no equivalent and needs none                     |
| `_fooLD.postValue(Event(x))` (Channel target)                                                                           | `_fooEvent.send(x)` from a suspend/`viewModelScope` context; `_fooEvent.trySend(x)` only from non-suspend code                                                |
| `_fooLD.postValue(…)` inside a `catch (exception: Exception)` block (Channel target)                                    | `_fooEvent.trySend(…)`, not `send`: see "Emitting from a catch block" below                                                                                   |
| `fooLD.observe(viewLifecycleOwner) { handleFoo(it) }`                                                                   | one `launch { viewModel.fooFlow.collect { handleFoo(it) } }` per flow, inside `repeatOnLifecycle(STARTED)`; see below                                         |
| `it.getContentIfNotHandled()?.let { … }` in the observer                                                                | Body runs directly on the collected value; the unwrapping disappears                                                                                          |
| `MediatorLiveData` (or the project's helper built on it)                                                                | `combine(flowA, flowB) { a, b -> … }`                                                                                                                         |
| `fooLD.observeForever(observer)`                                                                                        | Collect in a scope you cancel explicitly; do not leave a dangling collector                                                                                   |

### Emitting from a catch block

Legacy ViewModels often wrap each job in `try { … } catch (exception: Exception) { … }` and emit a
loader-off or error value from the `catch`, sometimes through a job-failure helper whose lambdas
are not `suspend`. The `catch` also runs on `CancellationException`, when the previous job is
cancelled by a new call (`saveJob?.cancel()`). In a cancelled coroutine `send` throws immediately,
so the rest of the `catch` block is skipped; `postValue` never did that. Use `trySend` everywhere
inside `catch`.

### Coexisting with LiveData

The Fragment usually keeps other LiveData to observe, so its observe method (the profile names the
convention, `initLiveData()` in the examples) stays. Add an `initFlows()` method next to it, call
it right after the observe method in `onViewCreated`, and move only this ViewModel's observers into
it. Delete the observe method (and the call) only when the migration empties it; an empty one left
behind is a half-rename.

When a Fragment already has an `initFlows()` from an earlier migration, add a `launch { … }` line to
it rather than creating a second method.

### Collection in a Fragment

```kotlin
private fun initFlows() {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // One launch PER flow - a bare collect never returns and would
            // starve every statement after it.
            launch { viewModel.menusFlow.collect { handleMenus(it) } }
            launch { viewModel.selectedMenuSectionFlow.collect { handleSelectedMenuSection(it) } }
            launch { viewModel.updateProfileEvent.collect { handleUpdateProfile(it) } }
            // Through a host ViewModel: same pattern, full path
            launch { getProspectViewModel.saveUserViewModel.userSavingEvent.collect { handleSavedUser(it) } }
        }
    }
}
```

In an **Activity** use `lifecycleScope` + `repeatOnLifecycle` on the Activity itself; there is no
`viewLifecycleOwner`.

In a **Dialog, custom View or Adapter** there is no lifecycle owner of its own. Do not fabricate
one: hoist the collection into the owning Fragment and pass the value in, or pass a
`LifecycleOwner`/`CoroutineScope` explicitly at construction.

A screen already in **Compose** collects a `StateFlow` with `collectAsStateWithLifecycle()` in the
host's `setContent` (or the navigation destination) and passes the value down; `Channel` events
stay collected in the host as above, or in a `LaunchedEffect` with `repeatOnLifecycle(STARTED)`.

## Common Mistakes (What Naive Agents Do Wrong)

| Mistake                                                                              | Why It Is Wrong                                                                                                                                                                                                                                                                                                                                                                                                       |
|--------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Converting `MutableLiveData<Event<T>>` to `StateFlow`                                | `StateFlow` replays its current value to every new collector. `repeatOnLifecycle` re-collects on every STARTED transition, so returning from the back stack re-fires the navigation, re-shows the error dialog, re-submits the form. This is exactly what the wrapper's "handled" flag was built to prevent. Use a `Channel`.                                                                                         |
| Keeping the event wrapper inside a `Channel`                                         | A `Channel` already delivers each element exactly once. `Channel<Event<T>>` means every consumer must still call `getContentIfNotHandled()` for no reason, and a second collector gets `null` instead of nothing. Unwrap to `Channel<T>`.                                                                                                                                                                              |
| Writing `Channel<T>()` without a capacity                                            | The default is `Channel.RENDEZVOUS`: no buffer. `repeatOnLifecycle` cancels the collector on STOPPED, so every `trySend` while the UI is off-screen returns failed and is **silently dropped**; a rotation loses the navigation event with no error anywhere. Always `Channel<T>(Channel.BUFFERED)`.                                                                                                                  |
| Assigning `_fooFlow.value = …` instead of `_fooFlow.update { … }`                    | `value` is a plain setter, so a read-modify-write such as `_fooFlow.value = _fooFlow.value?.copy(isLoading = false)` is two separate operations. ViewModels that emit from concurrent `Dispatchers.IO` coroutines lose a write that lands between the read and the write. `update { }` applies the change atomically via compare-and-set. Reading `.value` stays as it is; only writes change.                          |
| Stripping `?.` null-safety from `StateFlow.value`                                    | Only valid when the flow has a **non-null** initial value. LiveData constructed empty becomes `StateFlow<T?>`, and `null` is a live state; the existing observers already take `T?`. Removing the check causes NPEs on first frame.                                                                                                                                                                                  |
| Assuming `StateFlow` re-emits equal values                                           | `MutableLiveData.setValue` always notifies; `MutableStateFlow` conflates by `equals`. When the result type's subtypes are **data classes** (check the profile), emitting the same `Loading(isVisible = true)` twice, or the same `Error(TECHNICAL)` after a failed retry, delivers **one** emission and the UI never re-renders. If a screen depends on repeated identical emissions, it is an event: use a `Channel`. |
| Copying a `repeatOnLifecycle(Lifecycle.State.CREATED)` found elsewhere in the code   | `CREATED` keeps collecting while the Fragment is stopped, so UI is touched off-screen and back-stack Fragments keep reacting. UI collection uses `STARTED`, whatever the codebase uses `CREATED` for (often legacy DI in `init {}` blocks).                                                                                                                                                                           |
| `lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(…) }` in a Fragment    | Mixes the Fragment lifecycle with the view lifecycle; the coroutine outlives the view and re-registers on every view recreation. Use `viewLifecycleOwner.lifecycleScope`.                                                                                                                                                                                                                                             |
| Sequential `collect` calls inside one `repeatOnLifecycle` block                      | The first `collect` suspends forever; every later observer is silently never registered. One `launch` per flow.                                                                                                                                                                                                                                                                                                       |
| Removing `InstantTaskExecutorRule` or `androidx.arch.core.testing` without checking  | Only remove test infrastructure that is live: the profile says whether either is declared or used. Editing a commented-out test or a dependency that was never declared adds noise to the diff.                                                                                                                                                                                                                      |
| Inventing conversions for `SingleLiveEvent`, `observeNotNull`, `TestObserver`        | Convert only what the project has (the profile lists which of these exist).                                                                                                                                                                                                                                                                                                                                           |
| Reordering imports to satisfy a linter the project does not run                      | Check the profile: with no linter, leave unrelated import blocks alone; the diff should contain only the migration.                                                                                                                                                                                                                                                                                                   |
| Declaring the ViewModel done because it compiles                                     | Compilation cannot catch a lost event or a conflated state. Every converted property needs its call sites read.                                                                                                                                                                                                                                                                                                       |

### Channel delivery caveat

`Channel.BUFFERED` + `repeatOnLifecycle(STARTED)` covers rotation and ordinary backgrounding, but it
does **not** guarantee delivery. Because of coroutine prompt cancellation (kotlinx.coroutines 1.4+),
an element already dequeued from the buffer but not yet handled when the lifecycle hits STOPPED is
lost with no error.

That is acceptable for a toast or a one-shot refresh. It is not acceptable for navigation or for a
payment/transfer confirmation: losing those strands the user mid-flow. Model those as nullable
state that the UI clears after consuming:

```kotlin
// ViewModel - suffix is Flow, not Event: the suffix follows the type, and this is a StateFlow
private val _navigationFlow = MutableStateFlow<TransferDestination?>(null)
val navigationFlow: StateFlow<TransferDestination?> = _navigationFlow.asStateFlow()

fun onValidated(destination: TransferDestination) { _navigationFlow.update { destination } }
fun onNavigationHandled() { _navigationFlow.update { null } }

// Fragment
launch {
    viewModel.navigationFlow.collect { destination ->
        destination?.let {
            navigateTo(it)
            viewModel.onNavigationHandled()
        }
    }
}
```

This survives process death of the collector and re-delivers on the next STARTED, at the cost of the
explicit `onNavigationHandled()` call. When `Channel` is new to the codebase, the first ViewModel to
land one sets the precedent: the profile names it, so follow its shape.

## Test patterns

When the module has live tests for the ViewModel, or when you add one:

### State: read `.value` directly

```kotlin
// StateFlow.value is synchronously readable; no observer, no rule, no waiting
assertEquals(DataResult.Loading(isVisible = true), viewModel.fooFlow.value)
assertTrue(viewModel.fooFlow.value is DataResult.Success)
```

### Channel-backed events: collect before triggering

```kotlin
@Test
fun `should emit the questions when loading them succeeds`() = runTest {
    val results = mutableListOf<DataResult<FaqResponse>>()
    // backgroundScope is auto-cancelled at the end of runTest - no manual job.cancel()
    backgroundScope.launch { viewModel.faqEvent.toList(results) }

    viewModel.loadFaqQuestions()
    advanceUntilIdle()

    assertEquals(1, results.size)
    assertTrue(results[0] is DataResult.Success)
}
```

Collect **before** the trigger: a `Channel` does not replay, so a collector started afterwards sees
nothing. Test both the "event fires" and the "event does NOT fire" path; the second is what catches
a `StateFlow` accidentally left in place of a `Channel`.

### The `Dispatchers.IO` caveat

When the ViewModel launches with `viewModelScope.launch(Dispatchers.IO)`, `Dispatchers.setMain(...)`
does **not** replace `Dispatchers.IO`, so `advanceUntilIdle()` has nothing to advance and the
assertion races the real IO thread pool. If you need a deterministic test, inject the dispatcher
into the ViewModel rather than adding `delay()` to the test. Do not silently change
`Dispatchers.IO` to make a test pass: that is a behaviour change outside this migration's scope.
Flag it instead.

## Verification

When the project has no linter and little ViewModel test coverage (see the profile), verification is
compilation plus grep plus call-site review. Compile **every module that holds a call site**, not
only the ViewModel's own, with the variant task the profile gives (never a guessed
`compileDebugKotlin` in a project with flavors):

```bash
# Real gate: the ViewModel's module plus each module with a call site
./gradlew :common:requests:<compileVariantKotlin> \
          :features:login:<compileVariantKotlin>
# Only if one of them has a live test for this ViewModel:
./gradlew :<module>:<testVariantUnitTest>

# Must return nothing in the ViewModel file (add the project's event wrapper name):
grep -n "LiveData\|postValue\|<EventWrapper>" path/to/FooViewModel.kt

# No old property or handler name may survive anywhere, main or test:
grep -rn "fooLD\|barLD\|handleFooLD" --include='*.kt' . | grep -v /build/
```

If the event wrapper still appears in the ViewModel, either a property was missed or the wrapper was
carried into a `Channel`. If an old name appears, the rename stopped halfway. The Fragments will
still contain `LiveData`, `.observe(` and the wrapper for other ViewModels; that is expected, so do
not grep them for those words.

Then re-read every converted property and confirm, per property:

- **Event vs state** decided from actual call sites (`getContentIfNotHandled` vs `peekContent`), not from the type name.
- **Nullability** on `StateFlow<T?>` preserved at every consumer.
- **No conflation loss**: nothing relies on two consecutive equal result values.
- **Read-only exposure**: no public `MutableStateFlow` or `Channel`.

## Commit structure

**One commit per ViewModel.** The ViewModel, every Fragment/Activity/Dialog that reads it and its
tests change together, across modules: splitting them produces a commit that does not compile,
because a converted property breaks every `observe()` call site immediately.

The commit scope is the ViewModel's own module (`refactor(requests): …` for a ViewModel of
`:common:requests`), even when most of the lines land in the modules that consume it. Do not amend
unrelated changes into it: the commit should contain only the LiveData migration for that one
ViewModel. Use the `commit` skill for the message.

### Before staging

1. The compile gate above, on every touched module; must succeed.
2. Run the grep gate above; must return nothing.
3. Read the full diff (`git diff`) and check:
   - No LiveData remnant in the ViewModel, and no event wrapper carried into a `Channel`.
   - Only this ViewModel's observers moved; neighbouring LiveData observers untouched.
   - No `send` inside a `catch` block.
   - Every `Channel` has an explicit `Channel.BUFFERED` capacity.
   - Every `StateFlow` write goes through `update { }`, not `value =`.
   - Suffixes match the target type: `fooFlow` for `StateFlow`, `fooEvent` for `Channel`.
   - Nullability preserved on every `StateFlow<T?>` consumer.
   - No public `MutableStateFlow` / `Channel` exposure.
   - No unrelated import reshuffling, reformatting, or `Dispatchers` change.
4. Fix what you found, then commit.
