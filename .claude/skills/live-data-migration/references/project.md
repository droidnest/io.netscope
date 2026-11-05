# Project profile: NetScope (Android library, `io.netscope`)

**Match check:** `settings.gradle.kts` has `rootProject.name = "NetScope"` and includes `:netscope-core`. If it does
not, this profile describes another project: rewrite the "This project" column from "How to find it" and update this
check.

**This project has no LiveData** (`grep -rl LiveData --include='*.kt' .` finds nothing): its ViewModels already expose
`StateFlow`. The skill does not apply here until LiveData appears; keep this profile so the check stays one grep.

## Role names

| Role in the skill | How to find it | This project |
|---|---|---|
| `Event<T>` (single-consumption wrapper) | `open class` with `getContentIfNotHandled()` / `peekContent()` | *none* |
| `DataResult<T>` (result type held in LiveData) | Type most often found inside `MutableLiveData<…>` | *none*: repositories return values, nullable values and `Flow` (`TransactionRepository`) |
| Job-failure helper | Function called in the `catch` of `viewModelScope.launch` | *none* |
| Legacy property suffix | Names of `LiveData` properties | *none* |
| Observe convention | Where screens call `.observe(` | *none*: screens are Compose destinations reading `StateFlow` |
| `MediatorLiveData` helper | `grep -rl MediatorLiveData` | *none* |
| Channel precedent | First ViewModel exposing a `Channel` | *none* |

## Codebase facts

| Question | How to find it | This project |
|---|---|---|
| Do screens use Compose? | `ComposeView` / `setContent` | Yes: single `NetScopeActivity` with Navigation Compose (`NetScopeNavGraph`); no Fragments, no XML layouts |
| `SingleLiveEvent`, `observeNotNull`, `TestObserver`? | `grep -rn` for each | None |
| `InstantTaskExecutorRule` / `androidx.arch.core.testing`? | grep sources and `libs.versions.toml` | Neither |
| Linters / `.editorconfig`? | Root files, Gradle plugins, CI | None in the build or in `.github/workflows/publish.yml` (`CONTRIBUTING.md` mentions ktlint, but nothing runs it) |
| Dispatchers in ViewModels | `grep -rn "launch(Dispatchers"` | ViewModels launch on `viewModelScope` without a dispatcher |
| Live ViewModel tests | `src/test` of the modules | None |

## Commands

| Item | How to find it | This project |
|---|---|---|
| Compile a module | `./gradlew :<module>:tasks --all \| grep compile.*Kotlin` | `./gradlew :netscope-ui:compileDebugKotlin` (no product flavors) |
| Run a module's tests | Same | `./gradlew :<module>:testDebugUnitTest` |
