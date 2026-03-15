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

git commit -am "chore: release v0.2.0"
git tag v0.2.0
git push origin main --tags

# GitHub Actions will automatically publish to Maven Central
```

## Code Style

- Follow Kotlin official style guide
- Use `ktlint` (enforced in CI)
- All public APIs must have KDoc comments

## License

By contributing you agree your contributions will be licensed under Apache-2.0.
