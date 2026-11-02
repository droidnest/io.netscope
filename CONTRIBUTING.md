# Contributing to NetScope

Thank you for your interest in contributing! 🎉

## Setup

```bash
git clone https://github.com/YOUR_USERNAME/netscope.git
cd netscope
./gradlew build
```

Requires: JDK 17, Android Studio Hedgehog or later.

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

```bash
# 1. Bump VERSION_NAME in gradle.properties
# 2. Update CHANGELOG.md
# 3. Commit and tag

git commit -am "chore(release): release v0.7.0"
git tag -a v0.7.0 -m "v0.7.0"
git push origin main --tags
```

Pushing a `vX.Y.Z` tag runs `.github/workflows/publish.yml`, which publishes to Maven Central. That workflow is
disabled at the moment, so a tag publishes nothing until it is enabled again.

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
./gradlew :netscope-core:testDebugUnitTest
./gradlew :netscope-ui:testDebugUnitTest
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
