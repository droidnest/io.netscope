# Contributing to NetScope

Thank you for your interest in contributing! 🎉

## Setup

```bash
git clone https://github.com/droidnest/io.netscope.git
cd io.netscope
./gradlew build
./gradlew :sample:installDebug   # demo app that wires every feature
```

Requires JDK 17 and an Android Studio release that supports AGP 9.1 (the Gradle wrapper is 9.3.1). The library
modules compile against SDK 35.

## Reporting bugs and ideas

Open an issue on [GitHub](https://github.com/droidnest/io.netscope/issues) with the NetScope version, the
OkHttp, Kotlin and Compose versions of your app, the Android version of the device, and the steps or stack trace.
A feature request is easier to accept with the problem it solves rather than only the API you would like.

Report a security issue, such as a redacted header showing up somewhere, privately through
[GitHub security advisories](https://github.com/droidnest/io.netscope/security/advisories/new) instead of a public
issue.

## Branching

- `main` — stable, release-ready
- `develop` — active development
- Feature branches: `feat/your-feature`
- Bug fixes: `fix/issue-description`
- Refactors: `refactor/what-changes`

## Pull Requests

1. Fork the repo and create your branch from `develop`
2. Make sure `./gradlew build` passes
3. Add tests if applicable
4. Open a PR against `develop`

## Releasing (maintainers only)

1. On `develop`, bump `VERSION_NAME` in `gradle.properties` and add the release to `CHANGELOG.md` and the README
2. Merge `develop` into `main` and push

```bash
git commit -m "chore(release): set the version to 0.8.0"
git checkout main && git merge develop && git push origin main
```

A push to `main` runs `.github/workflows/publish.yml`. When `vX.Y.Z` for the current `VERSION_NAME` does not
exist yet, it runs the unit tests, publishes every module and releases the deployment on the Central Portal, then
pushes the `vX.Y.Z` tag. Nothing has to be clicked on https://central.sonatype.com. A push whose version is
already tagged publishes nothing, so merging without a bump is safe. The artifacts reach Maven Central search a
few minutes after the run.

A hotfix from a `release/X.Y.x` branch is released by pushing its tag, which has to match `VERSION_NAME`:

```bash
git tag -a v0.7.5 -m "v0.7.5" && git push origin v0.7.5
```

The published modules are compiled for Kotlin 2.0 (`languageVersion`, `apiVersion` and `coreLibrariesVersion` in
the root `build.gradle.kts`), whatever Kotlin version AGP builds with, so apps on Kotlin 2.0 can read them. Raising
that target drops those apps, and belongs in a minor release with a line in the README requirements.

## Architecture

The [README](README.md#architecture) describes the layers and the UI flows. When changing them:

- A ViewModel receives use cases from its `Factory`, and only the `Factory` reads the `NetScope` object
- One use case per file, in `domain/usecase/<area>`, exposed as `operator fun invoke`
- A ViewModel's public members are `uiState` and `onIntent`
- A class takes its dispatcher as a constructor parameter rather than naming `Dispatchers.IO`
- Anything added to the public API of `NetScope`, `NotificationStyle`, `NetScopeNotificationManager` or
  `NetScopeShakeDetector` is mirrored in `netscope-no-op` with the same signature

## Testing

```bash
./gradlew testDebugUnitTest          # every module
./gradlew :netscope-core:testDebugUnitTest
./gradlew :netscope-ui:testDebugUnitTest
./gradlew :netscope-export:testDebugUnitTest
```

Tests use JUnit 4, `kotlinx-coroutines-test` and Turbine, with fakes in each module's `fake/` package. Name them
`` `should … when …` `` and build a ViewModel in `@Before`, so that `MainDispatcherRule` applies to it.

## Commits

Use conventional commits, `type(scope): subject`, where the scope is the module without its `netscope-` prefix:
`fix(core): keep the network timing when the response is saved`.

## Code Style

- Follow Kotlin official style guide (no formatter or linter runs in the build)
- Everything a consumer does not call is `internal`
- All public APIs must have KDoc comments
- A change to a public signature is breaking: note it in `CHANGELOG.md`

## License

By contributing you agree your contributions will be licensed under Apache-2.0.
