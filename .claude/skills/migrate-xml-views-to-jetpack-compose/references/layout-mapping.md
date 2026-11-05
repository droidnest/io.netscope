# XML layout to Compose

Adapted from the Android reference *xml-layout-migration*. Read with `theme-mapping.md` for the
colors and text, and with `project-mapping.md` (and the design system doc it points to) for the
project's custom views: every legacy view with a counterpart is deprecated and names its component.

## Order of preference

1. **An `App*` component** of the design system, when the XML view or the group of views it forms
   has a counterpart, even if the XML built it from plain views.
2. **A private composable of the feature**, assembled from foundation layouts, `Text` and `App*`
   components, for a block specific to the screen.
3. **A new `App*` component** in the design system module when the block is reusable (step 8 of
   the skill).
4. **`AndroidView`** only for a view with no Compose equivalent (`interoperability.md`).

A raw Material 3 component (`Button`, `Card`, `OutlinedTextField`, `Switch`) is never the answer
in a feature module, unless the profile says the project's design system is Material 3 itself.

## Containers

| XML                                     | Compose                                                                                                                       |
|-----------------------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| `LinearLayout` vertical / horizontal    | `Column` / `Row`; `gravity` → `horizontalAlignment` / `verticalAlignment`                                                     |
| `FrameLayout`                           | `Box`; `layout_gravity` → `Modifier.align`                                                                                    |
| `ConstraintLayout`                      | Nested `Row` / `Column` / `Box`. Add `constraintlayout-compose` only if the project already has it (profile)                  |
| `ScrollView`, `NestedScrollView`        | `Column(Modifier.verticalScroll(rememberScrollState()))`                                                                      |
| `RecyclerView`                          | The design system's list component when it has one (loading, error, empty states included); else `LazyColumn(key = { it.id })` |
| `GridLayoutManager`                     | `LazyVerticalGrid`, or the list component's columns parameter                                                                 |
| Horizontal `RecyclerView`               | `LazyRow`                                                                                                                     |
| `<include layout="...">`                | A call to the composable of that layout                                                                                       |
| `<merge>`                               | Nothing: the children go in the caller's layout                                                                               |
| `ViewStub`                              | `if (condition) { ... }`                                                                                                      |
| `Space`                                 | `Spacer(Modifier.height(x.dp))` or `Arrangement.spacedBy(x.dp)` when the gaps are equal                                       |

## Leaf views

The design system's counterpart wins over every row below (`project-mapping.md`).

| XML                                                    | Compose                                                                                                                         |
|--------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `TextView` (and custom text views)                     | `Text` + `AppTheme.typography` + `AppTheme.colors` (`theme-mapping.md`)                                                         |
| `ImageView` with `src`                                 | `Image(painterResource(...), contentDescription)`; `scaleType` → `contentScale`; `tint` → `colorFilter = ColorFilter.tint(...)` |
| A `View` of 1 dp used as a line                        | The design system's divider, else `HorizontalDivider`                                                                           |
| `CardView`, `MaterialCardView`, a rounded background   | The design system's card                                                                                                        |
| A label and a value side by side                       | The design system's detail row                                                                                                  |
| `EditText`, `TextInputLayout`                          | The design system's text field, or its specialized field (amount, phone, OTP, search)                                           |
| `SwitchCompat`, `CheckBox`, `RadioButton`              | The design system's toggle, checkbox, radio button                                                                              |
| `ProgressBar` (indeterminate)                          | The design system's loader                                                                                                      |
| `ProgressBar` (horizontal)                             | The design system's progress indicator, else `LinearProgressIndicator` themed by `AppTheme`                                     |
| `TabLayout`                                            | The design system's tabs                                                                                                        |
| `WebView`, `MapView`, camera preview                   | `AndroidView`                                                                                                                   |

## Attributes

| XML                                                               | Compose                                                                                |
|-------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| `layout_width="match_parent"`                                     | `Modifier.fillMaxWidth()`                                                              |
| `layout_width="wrap_content"`                                     | Nothing                                                                                |
| `layout_width="120dp"`                                            | `Modifier.width(120.dp)`                                                               |
| `layout_weight="1"`                                               | `Modifier.weight(1f)` inside a `Row` / `Column`                                        |
| `layout_margin*`                                                  | `Modifier.padding(...)` placed **before** `background` / `clickable` / `border`        |
| `padding*`                                                        | `Modifier.padding(...)` placed **after** them, or the component's own padding          |
| `minHeight`                                                       | `Modifier.heightIn(min = x.dp)`                                                        |
| `visibility="gone"`                                               | `if (visible) { ... }`                                                                 |
| `visibility="invisible"`                                          | `Modifier.alpha(0f)`, and no click handler while hidden                                |
| `background` color + `shape` drawable                             | `Modifier.background(color, shape)`                                                    |
| `onClick`, `setOnClickListener`, `?attr/selectableItemBackground` | `Modifier.clickable(onClick = ...)`, or the component's `onClick`                      |
| `contentDescription`                                              | `contentDescription` parameter, else `Modifier.semantics { contentDescription = ... }` |
| `textAllCaps="true"`                                              | `text.uppercase()`                                                                     |
| `maxLines` + `ellipsize="end"`                                    | `maxLines = n, overflow = TextOverflow.Ellipsis`                                       |
| `gravity="center"` on a text                                      | `textAlign = TextAlign.Center`                                                         |
| `elevation`                                                       | The card's elevation parameter; elsewhere, follow the design system (often flat)       |
| `android:text="@string/..."`                                      | `stringResource(...)` from the module's `R`, or the shared resources `R` (profile)     |

`Modifier` order matters: the margin is the outermost modifier, the padding the innermost.

## State

- A listener in the XML becomes a lambda parameter: `onConfirm: () -> Unit`, named after the
  action, not the view (`onConfirm`, not `onButtonClick`).
- A field's text, a toggle's state or a selected tab are held by the screen composable with
  `rememberSaveable` when only the UI needs them, or come from the Fragment when the ViewModel
  reads them.

## Example

Before:

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/recap_title"
        android:textAppearance="?attr/textAppearanceHeadline6" />

    <Button
        android:id="@+id/confirm"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:text="@string/confirm" />
</LinearLayout>
```

After (role names: write the project's theme and button):

```kotlin
@Composable
fun TransferRecapScreen(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.recap_title),
            style = AppTheme.typography.large.bold,
            color = AppTheme.colors.textPrimary,
        )
        AppButton(
            text = stringResource(R.string.confirm),
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        )
    }
}
```

The click listener the Fragment set on the binding is now a lambda. When the project has custom views,
`project-mapping.md` ends with the project's version of this example.
