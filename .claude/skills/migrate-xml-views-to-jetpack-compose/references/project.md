# Project profile: NetScope (Android library, `io.netscope`)

**Match check:** `settings.gradle.kts` has `rootProject.name = "NetScope"` and
`netscope-ui/src/main/kotlin/io/netscope/ui/component/NetScopeTheme.kt` exists. If not, this profile describes
another project: rewrite the "This project" column from "How to find it", rebuild `project-mapping.md` with the method
of `theme-mapping.md`, and update this check.

**This project has no XML layouts** (`find . -path '*/res/layout*' -name '*.xml'` finds none, and no module has a
`res/` folder): the UI is Compose from the start. The skill does not apply here; the rows below describe the Compose
side so that `feature-architecture` and new screens use the same names.

## Role names

| Role in the skill | How to find it | This project |
|---|---|---|
| `AppTheme` (theme composable, `.colors`, `.typography`) | `@Composable fun …Theme(` | `NetScopeTheme(isDark)` (`netscope-ui/.../ui/component/NetScopeTheme.kt`): provides `LocalNetScopeColors` only, no typography and no `MaterialTheme` wrapper. Colors are read through top-level accessors: `NetScopeDarkBackground`, `NetScopeSurface`, `NetScopeCard`, `NetScopeCardAlt`, `NetScopeSurface3`, `NetScopeBorder`, `NetScopeBorder2`, `NetScopeAccent`, `NetScopeMuted`, `NetScopeText2`, `NetScopeOnSurface`, `NetScopeOnCode`, `NetScopeCode`, `NetScopeDropdown` |
| Semantic colors | Constants next to the theme | `ColorSuccess`, `ColorAmber` (`ColorWarning`, `ColorRedirect`), `ColorError`, `ColorPending`, `ColorPurple`, `ColorPink`; helpers `StatusClass.color()`, `String.methodColor()`, `statusColor(code)` |
| `App*` components | Public composables of the design system | *none* as a library: shared composables live in `netscope-ui/.../ui/component/` (`TransactionItem`) without a prefix; screens use Material 3 components (`material3`, `material-icons-extended`) colored with the tokens above |
| Loader / loading dialog / popup | Components drawing loading and dialogs | *none*: no loader anywhere (data is local), Material 3 `AlertDialog` for confirmations; screens are Material 3 `Scaffold` + `TopAppBar` |
| List component | Component wrapping `LazyColumn` | *none*: `LazyColumn` |
| Legacy views | Custom views of a shared views module | *none* |
| Base Fragment and its insets hook | Superclass of Fragments | *none*: no Fragments. `NetScopeActivity` calls `enableEdgeToEdge()` and hosts `NetScopeNavGraph` |
| Typography | Typography object of the theme | *none*: `TextStyle` / `fontSize` set in each screen, monospace for code |
| Previews | `@Preview` annotations | *none* yet |

## Design system

| Item | How to find it | This project |
|---|---|---|
| Module | Module the theme lives in | `:netscope-ui` (no separate design system module) |
| Reference doc | `docs/`, README | *none* in the repo; the theme comments refer to a "design system v1.0" PDF spec |
| Project mapping | This skill | [project-mapping.md](project-mapping.md): nothing to map (no XML) |
| Catalog | App demoing the components | *none*; `:sample` is a demo app of the library, not of the components |
| Hard-coded colors | `grep -c "Color(0x"` in screens | `TransactionDetailScreen` (7) and `MockRulesScreen` (2) still use raw `Color(0x…)`: use the tokens in new code |

## Build

| Item | How to find it | This project |
|---|---|---|
| Compose plugin | `plugins {}` of a Compose module | `alias(libs.plugins.kotlin.compose)` + `platform(libs.compose.bom)` (BOM `2024.12.01`); no convention plugins |
| Lifecycle Compose | Version catalog | `libs.compose.lifecycle` (`lifecycle-runtime-compose`), `libs.compose.viewmodel` |
| SDK limit | `compileSdk` of the library modules | Library modules stay on `compileSdk = 35` (history: several dependencies were downgraded to keep 35); `:sample` uses 36. Do not bump the BOM or AndroidX libraries past what `compileSdk 35` supports |
| Compile a module | `./gradlew :<module>:tasks` | `./gradlew :netscope-ui:compileDebugKotlin` |
| Install the demo app | Same, on `:sample` | `./gradlew :sample:installDebug` |
| Commit scopes | `commit` skill profile | `ui` for `:netscope-ui` |
