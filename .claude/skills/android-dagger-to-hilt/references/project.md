# Project profile: NetScope (Android library, `io.netscope`)

**Match check:** `settings.gradle.kts` has `rootProject.name = "NetScope"` and includes `:netscope-core`. If not,
this profile describes another project: rewrite the "This project" column from "How to find it" during Phase 0 and
update this check.

**This project has neither Dagger nor Hilt** (`grep -rlE 'dagger|hilt'` over sources, build files and the version
catalog finds nothing). There is nothing to migrate, and the skill does not apply. Adding Hilt to a published
library would also force it on every consumer app: do not propose it for NetScope.

| Item | How to find it | This project |
|---|---|---|
| Application class | `@HiltAndroidApp` / `Application` subclass | *none* in the library; `:sample` has only `MainActivity` |
| Root legacy component | `@Component` without dependencies | *none* |
| Shared legacy components | `@Component` interfaces in shared modules | *none* |
| Injectors | `object …Injector` | *none* |
| Wiring | How objects are built | The `NetScope` object (service locator) + nested `ViewModelProvider.Factory` classes |
| Shared base with legacy injection | Base classes calling an injector | *none* |
| Bridges / escape hatches | `*BridgeModule`, `disableModulesHaveInstallInCheck` | *none* |
| Migration ledger | `docs/di-migration/` | *none* |
| Annotation processing | `kapt` / `ksp` in the build | KSP, for Room only (`ksp(libs.room.compiler)`) |
| Source compile | `./gradlew :<module>:tasks` | `./gradlew :<module>:compileDebugKotlin` |
| App assemble task | `:sample` tasks | `./gradlew :sample:assembleDebug` |
