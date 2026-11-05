# XML theme to the design system theme

Adapted from the Android reference *migrate-xml-theme-to-compose*. That reference builds a
Material 3 theme from the XML one; here the design system theme (`AppTheme`) already exists and is
the source of truth. This file is the method: how to map what a layout reads from the XML theme to
what the composable reads from `AppTheme`. The project's tables are in `project-mapping.md`; when
it is missing or describes another project, build it with this method and show it to the user.

Do not create a theme, a `ColorScheme`, a `Typography` or a `Shapes` object. A value with no
counterpart goes into the design system module (a color role, a component parameter), never into
the feature module.

## Colors

### Read how the XML colors work first

Open the app theme's `attrs.xml`, `themes.xml`, `colors.xml` (and `values-night/`) and the palette
file. XML themes usually mix:

- **palette attributes** (`?attr/primary_500`, `@color/neutral_200`): a fixed tone, the same in
  both modes;
- **mode-dependent attributes** (`?attr/<light>_to_<dark>`, or one name resolved differently in
  `values-night/`): a pair of tones, one per mode;
- **raw hex** in layouts or drawables.

In Compose, a mode-dependent color is a **role** of `AppTheme.colors` (`textPrimary`,
`textSecondary`, `background`…) or the color an `App*` component already owns. Pick the role the
color plays, not the hex.

### Build the color table

For the 10 to 20 attributes the feature layouts use most (`grep -rhoE '\?attr/[a-z_0-9]+'
features/*/src/main/res | sort | uniq -c | sort -rn`), write one row each:

| XML attribute | Light / dark values | Compose |
|---|---|---|

- Exact match → the role or token (`AppTheme.colors.textSecondary`).
- Near match (a few units off in hex) → the token, noted as an expected difference; never copy the
  XML hex.
- Fill of a field, a divider, a card surface → the component that draws it.
- A pair with no role → `if (AppTheme.colors.isDark) <dark> else <light>` with scale tokens, and
  propose a role to the user when the pair repeats across screens.
- Scales that do not follow the mode (a tone 50 that stays light in dark mode) → note it: the
  table must say which scale tones change with the mode.

## Typography

Map the XML text styles (`textAppearance`, a custom view's size and weight attributes, raw
`textSize` + `fontFamily`) onto `AppTheme.typography.<size>.<weight>`:

- one table for sizes (XML attribute value → sp → Compose size), one for weights;
- a weight the design system lacks (a 600 when it has 400 / 500 / 700) maps to the nearest one,
  noted as an expected difference;
- check each custom text view's source: some ignore `android:textSize` or set no line height, so
  trust the baseline screenshot over the attribute, and list the line-height shift as an expected
  difference instead of compensating with padding.

## Styles and shapes

Window and dialog styles have nothing to port. A component style in a layout (a custom view type, a
button type attribute, a background drawable) maps to a component and its parameters, never to a
`TextStyle` or a color copied inline:

| XML | Compose |
|---|---|
| A button with a leading icon | The design system button with its leading icon parameter |
| An icon-only button | The design system icon button |
| A filled / bordered button background | The button's style parameter |
| A `shape` drawable with `corners` | The component's shape; else `RoundedCornerShape(x.dp)` |
| `stroke` of a `shape` drawable | `Modifier.border(width, color, shape)` |

When the design system has no spacing or shape tokens, dimensions are the `dp` values of the XML,
named in a private `val` when they repeat.
