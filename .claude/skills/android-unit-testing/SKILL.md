---
name: android-unit-testing
description: Professional Android/Kotlin unit testing standard — Given/When/Then structure, naming, ViewModel/UseCase/Repository/Mapper tests, coroutines & Flow (runTest, Turbine, MainDispatcherRule), MockK vs fakes, Room/Retrofit/MockWebServer, Robolectric, coverage. Use this skill whenever the user asks to write, generate, review, fix, refactor, or explain unit tests for Android or Kotlin code, mentions JUnit, MockK, Mockito, Turbine, runTest, test coverage, flaky tests, "test this ViewModel/UseCase/Repository", Given-When-Then or Arrange-Act-Assert, or pastes Kotlin production code and asks for tests — even if they don't say "unit test" explicitly.
---

# Android Unit Testing (Professional Standard)

Write tests a senior reviewer would approve on the first pass: readable as a spec, deterministic, fast, and testing behavior rather than implementation.

## Project profile

[references/project.md](references/project.md) gives this repository's test libraries, its shared testing module (`MainDispatcherRule`, base test class, Flow helpers), where fakes go and the test commands. Where it differs from the defaults below (an assertion library, a missing Turbine), the profile wins. Its match check names the repository it describes: when the check fails in the repository you are in, rewrite `project.md` in place (git keeps the old one), filling its "This project" column from "How to find it" and updating the match check, before writing tests.

## Workflow

1. **Read the production code first.** Identify the unit (class under test), its dependencies, its public API, and every observable outcome (return values, emitted states/events, side-effect calls that matter to the contract).
2. **List scenarios before writing code.** Happy path, each error branch, edge cases (empty, null, boundary, duplicate), and concurrency/ordering if Flows are involved. One scenario = one test.
3. **Pick test doubles** (see "Fakes vs mocks" below).
4. **Write tests** using the template below.
5. **Self-review** against the checklist at the end before returning.

When the user pastes code and asks for tests, return: a short scenario list, then the complete test class (imports included, compilable), then any required Gradle deps or helper classes (e.g. `MainDispatcherRule`) that aren't obviously present. Keep prose minimal.

## Test template

```kotlin
class LoginUseCaseTest {

    private val authRepository: AuthRepository = mockk()
    private lateinit var sut: LoginUseCase

    @Before
    fun setUp() {
        sut = LoginUseCase(authRepository)
    }

    @Test
    fun `should return the session when the credentials are valid`() = runTest {
        // Given
        val session = Session(token = "abc")
        coEvery { authRepository.login("user", "pass") } returns Result.success(session)

        // When
        val result = sut("user", "pass")

        // Then
        assertThat(result.getOrNull()).isEqualTo(session)
    }
}
```

Rules behind this template:

- **Given / When / Then comments** in every test with more than 2 lines. Omit `// Given` when there is no setup. They make the single action obvious to the reviewer.
- **Exactly one When.** Two actions means two tests (or the test is really an integration test).
- **Name the unit `sut`** (system under test) or its natural name — be consistent within a codebase; follow existing convention if the project has one.
- **Test names:** backtick sentences `` `should Z when Y` `` for JVM tests in `src/test`: the expected outcome first, then the action or condition that triggers it, lowercase and without commas. When both an action and a precondition matter, end with `given X`: `` `should send the names and an empty company name when mapping a rename request given a person` ``. Never `given X, when Y, then Z`. In `src/androidTest` use `shouldZ_whenY()` (or `shouldZ_whenY_givenX()`) because backtick names with spaces fail on older Android runtimes. The Given / When / Then comments stay in the test body.
- **Fresh state per test.** Create the SUT in `@Before` or inline; never share mutable state across tests.
- **Assertions:** prefer Truth (`assertThat`) or kotlin.test for readable failures, unless the profile names the project's library; never add an assertion library to a module on your own. Assert on the whole object when feasible (data class equality) rather than field by field.
- **Test data builders** for complex models: `fun aUser(id: String = "1", name: String = "Ali") = User(id, name)` — defaults keep each test focused on the field it cares about.

## Fakes vs mocks

- **Prefer fakes** for repositories, data sources, clocks, and anything with state. They survive refactors and read naturally (`fakeRepo.accounts = listOf(...)`).
- **Use MockK** for thin collaborators where you only need to stub one call or verify an interaction that *is* the contract (analytics event sent, navigation triggered).
- **Verify sparingly.** `verify` only what the caller would notice if missing. Don't `verify` getters you already assert through the result.
- **Never mock** data classes, value objects, or the class under test. Avoid `relaxed = true` except for fire-and-forget collaborators like loggers; relaxed mocks hide missing stubs.
- **Never mock Android framework types** in pure unit tests — push them behind an interface (`StringProvider`, `Clock`, `ConnectivityChecker`). If the code can't be tested without `Context`, say so and suggest the refactor, or use Robolectric as a last resort.

Details and fake examples: `references/test-doubles.md`.

## Coroutines & Flow (short version)

- Every suspending test uses `runTest`.
- Any class touching `Dispatchers.Main` / `viewModelScope` needs `MainDispatcherRule`.
- **Inject dispatchers** (`CoroutineDispatcher` constructor param) instead of hardcoding `Dispatchers.IO`; pass the test dispatcher in tests.
- Use **Turbine** (`flow.test { }`) for Flow/StateFlow/SharedFlow assertions; always end with `cancelAndIgnoreRemainingEvents()` or `expectNoEvents()` when appropriate.
- Use `advanceUntilIdle()` / `advanceTimeBy()` for debounce, delay, retry — never `Thread.sleep`.

Full patterns, StateFlow conflation gotchas, one-shot events, and `backgroundScope`: `references/coroutines-flow.md`.

## Layer recipes

Read `references/layer-recipes.md` when testing a specific layer:
- ViewModel (UiState + one-shot events, SavedStateHandle)
- UseCase / Interactor
- Repository (cache + network, error mapping)
- Mapper / pure functions (parameterized tests)
- Retrofit API with MockWebServer
- Room DAO (in-memory, Robolectric or androidTest)

## Project setup

Gradle version catalog, `MainDispatcherRule`, JUnit 5 option, Kover coverage config, and CI command: `references/setup.md`. Only include setup in your answer if the user's project appears to lack it (check the profile first) or they ask.

## What NOT to test

- Framework/library code (Retrofit parsing itself, Room SQL generation, Hilt wiring).
- Private functions directly — test through the public API. If a private function is complex enough to need its own tests, suggest extracting it.
- Trivial getters/data classes with no logic.
- Implementation details that would break on a harmless refactor (call order of internal helpers, exact number of times a cache was read).

## Self-review checklist

Before returning tests, confirm:
- [ ] Each test has one reason to fail and one When.
- [ ] Names follow `should … when …` and describe behavior, not method names (`should show the error when …` not `testLoadData2`).
- [ ] No `Thread.sleep`, no real network/disk/time, no reliance on test order.
- [ ] Every error branch in the production code has a test.
- [ ] Flows: all emissions asserted, including initial state; no hanging collectors.
- [ ] Mocks: no unnecessary `verify`, no `relaxed` hiding bugs.
- [ ] Compiles: imports present, `runTest` for suspend, `coEvery`/`coVerify` for suspend functions.
- [ ] If production code is hard to test (hardcoded dispatcher, `Context`, static calls, `System.currentTimeMillis()`), point it out with a concrete refactor suggestion.

## Reviewing existing tests

When asked to review tests, report findings grouped as **Bugs** (test passes but doesn't prove anything, flaky, wrong assertion), **Missing coverage** (untested branches), and **Style** (naming, structure). Give a corrected snippet for each bug.
