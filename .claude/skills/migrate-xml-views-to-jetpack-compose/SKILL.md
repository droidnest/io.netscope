---
name: migrate-xml-views-to-jetpack-compose
description: 'Use when migrating one XML layout of an Android app (a Fragment, Activity, dialog or list item layout in a feature module) to Jetpack Compose built on the project''s own design system and theme, keeping visual parity with the XML screen and hosting the result through ComposeView, after moving its ViewModels from LiveData to flows in a separate commit.'
---

# XML Views to Jetpack Compose Migration

Adapted from the Android skill
[migrate-xml-views-to-jetpack-compose](https://github.com/android/skills/tree/main/jetpack-compose/migration/migrate-xml-views-to-jetpack-compose).
The upstream workflow is kept; what changes is that the project already has its Compose
infrastructure, and that the migrated UI is built from the project's design system, not from
Material defaults.

## Project profile

Read [references/project.md](references/project.md) first. It maps the role names used below to the
project: `AppTheme` (the design system theme, with `AppTheme.colors` and `AppTheme.typography`), the
component prefix (`App*` below: `AppButton`, `AppCard`, `AppLoader`…), the legacy view prefix, the
design system module, doc, catalog, preview annotations, deprecation format, Gradle plugins and
commands. Write the project's names, never the role names. Its match check names the repository it
describes: when the check fails in the repository you are in, rewrite `project.md` and
`project-mapping.md` in place (git keeps the old ones), filling the "This project" column from "How to
find it" and updating the match check, show the user what you found, then start. A role the project
lacks (no catalog, no design system doc) is marked *none*: skip the steps that need it and say so in
the report. When the profile says the project has no XML layouts, there is nothing to migrate: say so
and stop.

## Scope: one layout at a time, in two commits

The unit of work is **one XML layout** and the class that inflates it. It lands in two separate
commits, in this order:

1. **The ViewModels of the screen move from LiveData to flows** (step 5), with the
   `live-data-migration` skill, while the screen is still XML. That commit changes no UI, so it
   is checked on the unchanged screen.
2. **The layout moves to Compose** (steps 6 to 11), reading the flows of the first commit.

The repositories, the dependency injection and the navigation graph stay as they are in both.
When the screen is also moving to the feature architecture (MVI on `UiState<T>` and `onIntent`,
layers, naming), the `feature-architecture` skill sets the shape of the ViewModel, the screen and
the Fragment; this skill keeps the visual parity.

## What the project already has

Do not set any of this up again; reuse what the profile lists: the Compose convention plugin (BOM,
compiler, tooling), the theme, the component library, its reference doc (tokens, component list,
the mapping from legacy views, the views with no component, the conventions), the preview
annotations, the catalog app, the deprecation of each legacy view that has a counterpart, and the
way shared resources are imported.

**Material 3 is not the design system.** The theme may map its roles onto `MaterialTheme` so a raw
Material component still looks consistent, but screens use the `App*` components and `AppTheme`
tokens. Never read `MaterialTheme.colorScheme` or `MaterialTheme.typography` in feature code. (When
the profile says the project *is* built on raw Material 3 with only color tokens on top, follow the
profile.)

## References

| File                                                                 | Read at       | Content                                                                                   |
|----------------------------------------------------------------------|---------------|-------------------------------------------------------------------------------------------|
| [references/project-mapping.md](references/project-mapping.md)       | Steps 2, 7, 8 | This project's XML colors, text styles, styles and legacy views to `AppTheme` and components |
| [references/theme-mapping.md](references/theme-mapping.md)           | Steps 2, 7    | How to map an XML theme onto an existing design system theme, and how to extend the mapping |
| [references/layout-mapping.md](references/layout-mapping.md)         | Steps 2, 8    | XML containers, views and attributes to composables and modifiers, with an example        |
| [references/interoperability.md](references/interoperability.md)     | Step 9        | `ComposeView` in a Fragment, base Fragment insets, state and events, `AndroidView`        |

## The 11 steps

1. Identify the candidate
2. Analyze the project and the layout
3. Plan
4. Capture the XML screen
5. Migrate the ViewModels to flows (first commit)
6. Enable Compose in the module
7. Theme
8. Write the composable
9. Replace the usages
10. Validate
11. Remove the XML (second commit)

### Step 1: identify the candidate

If the user named the layout, go to step 2. Otherwise, rank 3 to 5 layouts of the feature modules
(`features/*/src/main/res/layout`, or the profile's module layout) and let the user choose:

- **Leaf first**: an item layout, a dialog or a small screen, before a host screen with nested
  fragments.
- **Few views, little logic**: the fewest children, no data binding, few states.
- **Covered by the design system**: every legacy custom view the layout holds is deprecated and has
  a row in the design system's mapping from legacy views. A view with no component is either
  replaced by Compose itself (a custom text view → `Text`), still to design (a new component first,
  see step 8), or a feature block rebuilt as a private composable of the feature.
- **Little reuse**: a layout included from many places multiplies the regression surface.
- **Reachable**: a screen reachable on a device in a few taps, so step 4 can capture it.

For each candidate give the path, why, a complexity score from 1 (simplest) to 5, and the legacy
and custom views it holds.

### Step 2: analyze the project and the layout

1. **Baseline build.** Build the module's variant before touching anything (the profile gives the
   task; variant tasks follow the flavor). A pre-existing failure is reported and fixed first; do
   not start a migration on a broken build.
2. **Decompose the layout.** List:
   - Inputs: the data the screen shows, the LiveData it observes, the click listeners.
   - Views: each legacy view and its Compose counterpart from the mapping; each text style and its
     `AppTheme.typography` size and weight; each color and its `AppTheme.colors` role or scale tone
     (`references/project-mapping.md`).
   - Resources: strings, drawables, dimensions. Strings and drawables are reused, not copied.
   - Layout: margins, paddings, weights, constraints, scroll containers, visibility rules.
3. **Integration.** How the Fragment gets its ViewModel (Dagger, Hilt, `by viewModels`, an activity
   scoped one), what it observes, how it navigates (Safe Args), which dialogs it opens. List each
   ViewModel the screen observes and whether it still exposes LiveData.

### Step 3: plan

Write the plan from steps 1 and 2: the ViewModels to move to flows, the composables to create,
the view-to-component mapping, the state the Fragment passes down, the missing design system
pieces if any, the files deleted at the end, and the commits. Present it to the user and wait for
approval.

### Step 4: capture the XML screen

The baseline is a device screenshot of the XML screen, taken with the platform tools only. **Do
not add a screenshot testing library** (Paparazzi, Roborazzi, Shot, Dropshots…) and do not write a
screenshot test.

1. Ask the user for a screenshot or an absolute path first; it is the cheapest baseline.
2. Otherwise, with a device or emulator attached (`adb devices`):
   - Install the app variant (profile) and navigate to the screen. Before any tap, check that the
     screen is on and unlocked: `adb shell dumpsys power | grep mWakefulness` must say `Awake`, and
     `adb shell dumpsys window | grep isKeyguardShowing` must say `false`.
   - Locate the elements to tap with `adb shell uiautomator dump /sdcard/ui.xml` and their
     `bounds`, not with guessed coordinates. The user may be using the phone at the same time.
   - Capture: `adb exec-out screencap -p > <scratchpad>/xml_light.png`.
   - Capture the dark mode too (`adb shell cmd uimode night yes`, then `no` to restore it) when the
     design system has a dark theme.
3. Without a device, go on with the layout analysis alone and say so in the report.

Keep the screenshots in the scratchpad, never in the repository.

### Step 5: migrate the ViewModels to flows (first commit)

For each ViewModel the screen observes that still exposes LiveData, apply the
`live-data-migration` skill and follow it entirely: state to `StateFlow`, single-consumption events
to `Channel(Channel.BUFFERED)`, every call site collected in `repeatOnLifecycle(STARTED)`, in
whichever module it lives, one commit per ViewModel with that skill's scope and message rules.

- Do it on the XML screen, before any Compose code: the Fragment keeps its binding, and its
  handlers (`handleRecap(recap)`) still fill the views.
- Install the app and check that the screen still behaves as the baseline of step 4: same
  content, same navigation, no event fired twice on a back stack return.
- A ViewModel already on flows needs nothing. A ViewModel shared with other screens is migrated
  with all its call sites, as that skill requires; the other screens stay XML.

Commit before going on: the Compose migration never shares a commit with the LiveData migration.

### Step 6: enable Compose in the module

Apply the project's Compose plugin (a convention plugin, or the Kotlin Compose plugin and the BOM)
to the module and depend on the design system module (profile). Add the lifecycle Compose library
too: step 9 collects the state flows with `collectAsStateWithLifecycle`.

```kotlin
plugins {
    alias(libs.plugins.<feature-ui-plugin>)
    alias(libs.plugins.<compose-plugin>)      // the profile names both aliases
}

dependencies {
    implementation(projects.<design-system-module>)
    implementation(libs.<lifecycle-runtime-compose>)
}
```

Never declare Compose versions or the compiler by hand: the BOM and the plugin carry them. Do not
bump the BOM during a migration either: a newer Compose can require a newer `compileSdk` and AGP
(the profile notes the current limit). Build once to check the module still compiles.

### Step 7: theme

There is nothing to create: wrap the content in `AppTheme`, and map each XML color, text style and
component style with `references/project-mapping.md` (method in `references/theme-mapping.md`). Do
not write a module theme, a `MaterialTheme` wrapper or a local color object.

A value the screen needs that the design system lacks is added to the design system, not
hard-coded in the feature: a new color role or a component parameter in the design system module,
following its conventions, in its own change.

Drawables that use `?attr/` colors resolve against the Activity theme. They render in the app, but a
preview needs a themed context; wrap the preview in a `ContextThemeWrapper` of the app's XML theme
(profile).

### Step 8: write the composable

Map the containers, views and attributes with `references/layout-mapping.md`.

**Compose from the design system.**

- Replace each legacy view with its counterpart from the design system's mapping, with the
  parameters its note gives, and each text view with `Text` +
  `AppTheme.typography.<size>.<weight>` + `AppTheme.colors.<role>`.
- When the design system has special variants for a visual effect the XML built by hand (a blur
  or glass effect behind a field, a shadow tint), use the variant the mapping names (profile).
- Prefer an `App*` component to a hand-made row, card or button, even when the XML assembled it
  from plain views: a container holding a label and a value is the design system's detail row, a
  card with rows is its details card.
- No hard-coded color. Spacings and sizes are `dp` values read from the XML; a value repeated
  across the screen becomes a private `val` at the top of the file.
- Strings stay in resources (`stringResource`); reuse the existing keys.

**A view without a counterpart** (listed as having no component, or a custom view of the feature
reused elsewhere):

- If it is a reusable building block, create the component in the design system module first,
  following its conventions (prefix, generic name, `*Defaults` object, formatted values in,
  `Painter` for pictures, the component preview annotation, comments only for what the code cannot
  say). Then, as the design system doc requires (profile):
  - its demo page in the catalog app, with its icon and texts in the catalog's style;
  - its row in the component table and in the mapping table of the doc, and the view moved out of
    the list of views with no component;
  - the project's `@Deprecated` message on the legacy view, in the format of the other deprecated
    views (no `ReplaceWith`: a View cannot be replaced by a composable call).
- If it is specific to this screen, keep it as a private composable of the feature.
- An embedded view with no reasonable Compose equivalent (a map, a WebView, a camera preview)
  stays a View through `AndroidView`.

**Structure.**

- A stateless screen composable taking the values to show and the event lambdas, with
  `modifier: Modifier = Modifier` as first optional parameter. It never receives the ViewModel,
  a `Flow` or a `LiveData`.
- Formatting stays out of the composable: amounts, dates and masked numbers come in already
  formatted, as the design system components expect.
- A preview wrapped in `AppTheme`, with realistic data, for each significant state (loaded, empty,
  error). The screen composable of a Fragment or an Activity uses the project's device preview
  annotation (device frame, system bars). A part hosted in a `ComposeView` of a layout that stays in
  XML (a list item, a card) and every design system component use the cropped component preview
  annotation, never the device one.
- Comments only where the mapping is not obvious; never mention the XML view the code replaces.

### Step 9: replace the usages

Follow `references/interoperability.md`, which also covers the insets a base Fragment may apply to
a `ComposeView` root. The navigation stays Fragment based: the Fragment keeps its place in the
graph, its Safe Args and its ViewModel, and only its view becomes Compose.

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
                recap = viewModel.recapFlow.collectAsStateWithLifecycle().value,
                onConfirm = viewModel::confirm,
            )
        }
    }
}
```

**State from the flows of step 5.** The Fragment collects each `StateFlow` with
`collectAsStateWithLifecycle()` in `setContent` and passes the value down; the composable never
receives the ViewModel or the `Flow`. A ViewModel on the feature architecture exposes one `uiState`
and takes `onIntent`: pass `uiState = viewModel.uiState.collectAsStateWithLifecycle().value` and
`onIntent = viewModel::onIntent`, and send `Started` from `onCreateView` (when
`savedInstanceState == null`), before the `ComposeView` composes (the profile names a reference
screen).

The `Channel` events of a legacy ViewModel (navigation, toasts) stay collected and handled in the
Fragment in `repeatOnLifecycle(STARTED)`; they never reach the composable.

**Loader and popups move into Compose.** The composable draws the loading (the design system's
loader for a first load, its loading dialog over content it keeps drawn), the errors and the
confirmations (its popup, or a bottom sheet). The migrated Fragment no longer calls the legacy
popup and loader helpers the profile lists: a legacy `Channel` event that opened a popup becomes a
Compose state the Fragment sets and the popup's button clears.

**Partial migration.** When only part of a layout migrates (an included block, one card of a long
screen), replace that part with a `<androidx.compose.ui.platform.ComposeView>` in the XML and set
its content from the Fragment through the binding, with the same composition strategy. A
`RecyclerView` row becomes a `ComposeView` item only if the whole list is not migrating; when it
is, the list becomes the design system's list component when it has one (it takes over the
loading, error and empty states), or a plain `LazyColumn` when the list has none of them.

Remove the `ViewBinding` field of the Fragment once no XML is left behind it.

### Step 10: validate

1. Build the module and run its existing unit tests.
2. Install the app, open the migrated screen, and capture it in light and dark mode the same way
   as in step 4.
3. Compare each pair with the baseline (open both images). Ignore the text content; check the
   hierarchy, spacings, sizes, typography, colors, corner radii, icons, and the scroll and keyboard
   behavior. The expected differences are the ones the design system introduces on purpose (a
   component now matching the design system where the XML drifted); list them in the report
   instead of reproducing the drift.
4. Iterate until the remaining differences are only those.
5. Exercise the interactions: every click listener of the XML, the back press, the states the
   ViewModel can push (loading, error, empty).

### Step 11: remove the XML (second commit)

- Delete the migrated layout and the resources only it used (styles, dimens, layout-specific
  drawables). Search the whole repository for each one before deleting it, including
  `tools:layout` references in navigation graphs.
- Do not delete legacy views from the shared views module: other screens still use them. Their
  deprecation is the signal that they are on their way out.
- Commit the Compose migration with the `commit` skill, scoped to the feature module
  (`refactor(<module>): migrate the <screen> screen to Compose`). A component added to the design
  system on the way goes in its own commits before this one, with the design system's scope
  (component, catalog, deprecation, doc).
- Report what was migrated, the ViewModels moved to flows, the components used, the components
  created, the visual differences kept on purpose, and what was not verified on a device.

## Common mistakes

| Mistake                                                                       | Fix                                                                                       |
|-------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| `MaterialTheme.colorScheme.primary`, `Color(0xFF8000EB)`                      | The `AppTheme.colors` token or the role the XML color stands for                          |
| A `Card` + `Row` + `Text` rebuilt by hand                                     | The `App*` component from the mapping                                                     |
| A new `FeatureTheme` composable                                               | `AppTheme`                                                                                |
| Adding Paparazzi or Roborazzi for the baseline                                | `adb exec-out screencap`, compared by eye                                                 |
| Migrating the ViewModel in the Compose commit                                 | Step 5 first, its own commit, with the `live-data-migration` skill                        |
| Keeping LiveData and bridging it with `runtime-livedata`                      | Move the ViewModel to flows in step 5                                                     |
| `collectAsState()`, or collecting inside the screen composable               | `collectAsStateWithLifecycle()` in the Fragment's `setContent`, value passed down          |
| The legacy popup factory / loader helper kept in the migrated Fragment        | The design system's popup, loader and loading dialog in the composable                    |
| Passing the ViewModel or a `Flow` to the screen composable                    | Pass the values and lambdas; the Fragment owns the ViewModel                              |
| Deleting a legacy view whose last usage just moved                            | Keep it, deprecated                                                                       |
| A component created in the feature module and reused elsewhere                | Create it in the design system module, with its catalog demo and its doc rows             |
| A hand-made blur or translucent fill over a picture                           | The design system's variant for that effect, when the mapping names one                   |
| `RecyclerView` + empty / loading / error views rebuilt by hand                | The design system's list component                                                        |
| `@Deprecated(..., ReplaceWith(...))` on a newly deprecated view               | The message-only format the other deprecated views use                                    |
