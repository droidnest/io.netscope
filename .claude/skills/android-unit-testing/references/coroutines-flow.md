# Coroutines & Flow Testing

## Contents
1. MainDispatcherRule
2. Dispatcher injection
3. runTest basics & virtual time
4. Turbine for Flow / StateFlow / SharedFlow
5. StateFlow conflation gotcha
6. One-shot events (Channel / SharedFlow)
7. stateIn / WhileSubscribed and backgroundScope
8. Common mistakes

## 1. MainDispatcherRule

When the project has a shared testing module (see `project.md`), the rule lives there: add its `testImplementation(...)`
and import it, never copy it into a feature module. Its usual shape:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
```

`UnconfinedTestDispatcher` runs launched coroutines eagerly — simplest for ViewModels. Use `StandardTestDispatcher` when you need to assert intermediate states (e.g. `Loading` before `Success`) and control execution with `advanceUntilIdle()`.

Share the scheduler so virtual time is consistent:
```kotlin
@get:Rule val mainDispatcherRule = MainDispatcherRule()
@Test fun test() = runTest(context = mainDispatcherRule.dispatcher) { ... }
```

## 2. Dispatcher injection

Production:
```kotlin
class AccountRepository @Inject constructor(
    private val api: AccountApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun balance() = withContext(ioDispatcher) { api.balance() }
}
```
Test:
```kotlin
private val testDispatcher = StandardTestDispatcher()
private val sut = AccountRepository(fakeApi, testDispatcher)

@Test fun `...`() = runTest(testDispatcher) { ... }
```

## 3. runTest & virtual time

- `delay()` inside `runTest` is skipped instantly (virtual time).
- `advanceTimeBy(ms)` runs tasks scheduled before that time; `runCurrent()` runs tasks at current time; `advanceUntilIdle()` runs everything.
- `currentTime` gives the virtual clock — useful for asserting retry backoff.

```kotlin
@Test
fun `should send one request for the last query when typing fast`() = runTest(dispatcher) {
    // When
    sut.onQueryChanged("a"); sut.onQueryChanged("ab"); sut.onQueryChanged("abc")
    advanceTimeBy(301)
    runCurrent()

    // Then
    assertThat(fakeApi.queries).containsExactly("abc")
}
```

## 4. Turbine

```kotlin
@Test
fun `should emit Loading then Success when loading given the api succeeds`() = runTest {
    fakeRepo.result = Result.success(listOf(anAccount()))

    sut.uiState.test {
        assertThat(awaitItem()).isEqualTo(UiState.Idle)   // initial
        sut.load()
        assertThat(awaitItem()).isEqualTo(UiState.Loading)
        assertThat(awaitItem()).isEqualTo(UiState.Success(listOf(anAccount())))
        cancelAndIgnoreRemainingEvents()
    }
}
```

Key APIs: `awaitItem()`, `awaitError()`, `awaitComplete()`, `expectNoEvents()`, `skipItems(n)`, `expectMostRecentItem()`, `turbineScope { flowA.testIn(this); flowB.testIn(this) }` for multiple flows.

## 5. StateFlow conflation gotcha

StateFlow drops intermediate values if the collector is slower, and doesn't re-emit equal values. With `UnconfinedTestDispatcher` a `Loading` state set and replaced in the same frame may never be observed. Fixes:
- Use `StandardTestDispatcher` for Main and collect with Turbine before triggering the action.
- Or assert only the final state with `sut.uiState.value` if intermediate states aren't part of the contract.

## 6. One-shot events

Channel-based events:
```kotlin
private val _events = Channel<UiEvent>(Channel.BUFFERED)
val events = _events.receiveAsFlow()
```
Test:
```kotlin
sut.events.test {
    sut.onPayClicked()
    assertThat(awaitItem()).isEqualTo(UiEvent.NavigateToConfirmation)
    expectNoEvents()
}
```
For `MutableSharedFlow(replay = 0)`, start collecting **before** the action, otherwise the emission is lost.

## 7. stateIn / WhileSubscribed and backgroundScope

A `stateIn(viewModelScope, WhileSubscribed(5_000), initial)` flow only runs upstream while collected. Either collect with Turbine, or:
```kotlin
backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sut.uiState.collect() }
fakeRepo.emit(data)
assertThat(sut.uiState.value).isEqualTo(UiState.Success(data))
```
`backgroundScope` is cancelled automatically at the end of `runTest`, so no leaked collectors.

## 8. Common mistakes

- Using `runBlocking` → real delays, slow tests.
- Forgetting `MainDispatcherRule` → `IllegalStateException: Module with the Main dispatcher is missing`.
- Creating a new `StandardTestDispatcher()` without sharing `testScheduler` → time advances in one scheduler but not the other; test hangs or skips work.
- Asserting `uiState.value` right after `launch` with `StandardTestDispatcher` without `advanceUntilIdle()` → sees stale state.
- Not cancelling Turbine → "Unconsumed events" failure (good! it means an extra emission you didn't assert).
