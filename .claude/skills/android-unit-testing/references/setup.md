# Project Setup

Check latest stable versions before pinning; the versions below are examples.

## Version catalog (libs.versions.toml)

```toml
[versions]
junit = "4.13.2"
mockk = "1.13.13"
coroutines = "1.9.0"
turbine = "1.2.0"
truth = "1.4.4"
okhttp = "4.12.0"
robolectric = "4.14.1"
androidx-test = "1.6.1"
kover = "0.9.1"

[libraries]
junit = { module = "junit:junit", version.ref = "junit" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
mockk-android = { module = "io.mockk:mockk-android", version.ref = "mockk" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
truth = { module = "com.google.truth:truth", version.ref = "truth" }
mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-core = { module = "androidx.test:core-ktx", version.ref = "androidx-test" }

[bundles]
unit-test = ["junit", "mockk", "coroutines-test", "turbine", "truth"]

[plugins]
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }
```

Module `build.gradle.kts`:
```kotlin
dependencies {
    testImplementation(libs.bundles.unit.test)
    testImplementation(projects.core.testing)      // the shared testing module: MainDispatcherRule, base test, Flow helpers
}
android {
    testOptions {
        unitTests.isReturnDefaultValues = true      // only if needed; prefer abstractions
        unitTests.isIncludeAndroidResources = true  // for Robolectric
    }
}
```

## Shared testing module

One module (`:core:testing`, `:common:testing`… the profile names it) holds what every module's tests share: `MainDispatcherRule`, a base test class (stubbing `android.util.Log`, releasing the mocks after each test) and the Flow helpers. Modules depend on it with `testImplementation(...)`; put any helper two feature modules need there rather than copying it. When the project has none, propose creating it before the second module needs the same helper.

## JUnit 5 (optional)

Use the `de.mannodermaus.android-junit5` plugin. Replace `@Before` → `@BeforeEach`, rules → extensions (write a `MainDispatcherExtension` implementing `BeforeEachCallback`/`AfterEachCallback`). Gain: `@Nested`, `@DisplayName`, `@ParameterizedTest`.

## Coverage with Kover

```kotlin
kover {
    reports {
        filters { excludes {
            classes("*_Factory", "*_HiltModules*", "*.di.*", "*ComposableSingletons*", "*.BuildConfig")
            annotatedBy("androidx.compose.runtime.Composable")
        } }
        verify { rule { minBound(80) } }   // applied to domain/data modules
    }
}
```
Commands:
```bash
./gradlew testDebugUnitTest
./gradlew koverHtmlReport koverVerify
```

## CI

Run `testDebugUnitTest` + `koverVerify` on every PR; publish JUnit XML (`build/test-results/`) and the Kover HTML report as artifacts. Fail the build on flaky tests instead of retrying silently.
