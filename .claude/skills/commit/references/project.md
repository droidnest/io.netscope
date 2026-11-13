# Project profile: NetScope (Android library, `io.netscope`)

**Match check:** `settings.gradle.kts` has `rootProject.name = "NetScope"` and includes `:netscope-core`. If it does
not, this profile describes another project: rewrite the "This project" column from "How to find it", drop the rows
that do not apply, and update this check.

| Item | How to find it | This project |
|---|---|---|
| Module list | `include(...)` lines of `settings.gradle(.kts)` | `:netscope-core`, `:netscope-ui`, `:netscope-notification`, `:netscope-export`, `:netscope-no-op`, `:sample` |
| Modules whose scope is not their name | Scopes used for a module in `git log`, where the rules allow it | The shared `netscope-` prefix is dropped: `core`, `ui`, `notification`, `export`, `no-op`; `:sample` → `sample` (history: `feat(core): …`) |
| Scopes that are not modules | Recurring non-module scopes in `git log` | `publish` (Maven Central publishing and signing in the root `build.gradle.kts`, `gradle.properties`), `deps` (version catalog bumps), `release` (`VERSION_NAME` bump + `CHANGELOG.md`) |
| Release commits | `CONTRIBUTING.md`, release history | Two commits on `develop`: `docs: record the 0.8.0 release and point the install at it` (`CHANGELOG.md` and README), then `chore(release): set the version to 0.8.0` (`VERSION_NAME` in `gradle.properties`). No manual tag: merging into `main` makes CI publish and push `v0.8.0`. Features ship in their own commits before them |
| `ci` files | Pipeline files | `.github/workflows/publish.yml` |
| Types seen in history, never to reuse | Subjects of `git log --format=%s` | `design:` (→ `refactor(ui):` or `feat(ui):`) |
| Typical history messages not to imitate | Oldest-style subjects of `git log` | `feat: v0.6.0 — Analytics screen, bottom nav, detail redesign` (a release and three features in one), `design: apply design system v1.0 from PDF spec`, `chore: add whitespace to NetScope.kt`, `fix: downgrade core-ktx to 1.16.0 to support compileSdk 35` (a `deps` change with no scope) |
