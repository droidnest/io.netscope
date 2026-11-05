# Interoperability

Adapted from the Android references *compose-in-views* and *views-in-compose*, keeping what
applies to a Fragment based app: navigation through the Navigation component and Safe Args, screens
extending the project's base Fragment (profile).

Use `AndroidViewBinding` (needs `ui-viewbinding`) and `AndroidFragment` (needs `fragment-compose`)
only if the project already has them (profile). Do not add them for a migration.

## Compose in a Fragment

### Whole screen

The Fragment returns a `ComposeView` and keeps everything else: its ViewModel, its observers,
its navigation, its base Fragment hooks.

```kotlin
override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
): View = ComposeView(requireContext()).apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        AppTheme {
            TransferRecapScreen(
                beneficiary = viewModel.beneficiaryFlow.collectAsStateWithLifecycle().value,
                onConfirm = viewModel::confirm,
            )
        }
    }
}
```

### Part of a screen

The `ComposeView` takes the place of the migrated block in the XML, and the Fragment reaches it
through its binding:

```xml
<androidx.compose.ui.platform.ComposeView
    android:id="@+id/recap"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

```kotlin
binding.recap.apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent { AppTheme { RecapCard(...) } }
}
```

Several `ComposeView` in one layout each need their own id, or their saved state collides.

### Composition strategy

| Host                                          | Strategy                                                       |
|-----------------------------------------------|----------------------------------------------------------------|
| A Fragment's view, whole or partial           | `DisposeOnViewTreeLifecycleDestroyed`                          |
| A `RecyclerView` item                         | The default (`DisposeOnDetachedFromWindowOrReleasedFromPool`)  |
| An Activity                                   | `setContent` from `activity-compose`                           |

The default strategy in a Fragment disposes the composition when the view detaches, which loses
the Compose state on a back stack round trip.

### Insets applied by a base Fragment

Many base Fragments apply the system bar insets themselves, as View padding in `onViewCreated`, to
a view they look up in the root (a header, or the root's first child). A `ComposeView` root has no
child yet when `onViewCreated` runs, so that inset is silently not applied.

Read the base Fragment (profile) before hosting the screen: when it targets a child of the root,
override its hook so it targets the root itself, and do not also add `WindowInsets` padding in the
composable, or the inset is counted twice. When the base Fragment applies no insets, the composable
handles them (`Modifier.windowInsetsPadding` or the design system's scaffold). Check the top and
bottom of the screen on the device, in step 10.

### State and events

- The ViewModel exposes flows since step 5 of the skill. The Fragment collects each `StateFlow`
  with `collectAsStateWithLifecycle()` (lifecycle-runtime-compose) in `setContent` and passes the
  value to the screen composable. Not `collectAsState()`, which keeps collecting while the screen
  is stopped, and never the `Flow` or the ViewModel passed down.
- A ViewModel on the feature architecture gives the screen `uiState` and `onIntent`; the screen
  reacts to `Navigating` in a `LaunchedEffect` and calls the Fragment's navigation callback.
- One-shot events of a legacy ViewModel (`Channel`: navigation, toasts) stay collected and
  handled in the Fragment, in `viewLifecycleOwner.repeatOnLifecycle(STARTED)`.
- Loading, errors and confirmations are drawn by the composable with the design system's loader,
  loading dialog and popup. The migrated Fragment no longer calls the legacy popup or loader
  helpers (profile).
- Clicks go up as lambdas, and the Fragment acts on them: navigation, `startActivity`, the
  ViewModel call. A composable never navigates by itself and never reads `LocalContext` to start
  an Activity.

## Views in Compose

`AndroidView` hosts a View with no Compose equivalent (a map, a `WebView`, a camera preview). A
custom view of the project is rewritten instead, never wrapped.

```kotlin
AndroidView(
    factory = { context -> WebView(context) },
    update = { webView -> webView.loadUrl(url) },
    modifier = Modifier.fillMaxSize(),
)
```

- Build the View in `factory`; do not `remember` it outside `AndroidView`.
- `update` runs again whenever a state it reads changes: push the Compose state into the View
  there.
- In a `LazyColumn`, pass `onReset` (non-null) so the View is reused between items.

## Preview in the Layout Editor

A `ComposeView` left in an XML layout can show a composable's preview in the Layout Editor:

```xml
<androidx.compose.ui.platform.ComposeView
    android:id="@+id/recap"
    tools:composableName="<package>.RecapCardKt.RecapCardPreview"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```
