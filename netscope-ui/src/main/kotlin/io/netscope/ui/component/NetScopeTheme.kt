package io.netscope.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import io.netscope.model.NetTransaction

// ── Color token data class ─────────────────────────────────────────────────────

data class NetScopeColors(
    val background: Color,  // Bg — #0B0F1A
    val surface: Color,     // Surface1 — TopAppBar
    val card: Color,        // Surface2 — cards, tx rows
    val cardAlt: Color,     // Surface4 — input fields, code bg
    val surface3: Color,    // Surface3 — toggle off track, selected alt
    val border: Color,      // Border — dividers, strokes
    val border2: Color,     // Border2 — hover states
    val accent: Color,      // Cyan — primary accent, links, active
    val muted: Color,       // Text3 — muted, placeholder, meta
    val text2: Color,       // Text2 — secondary, labels, KV keys
    val onSurface: Color,   // Text1 — primary text
    val onCode: Color,      // Monospace path / code text
    val code: Color,        // Code block background (= Surface4)
    val dropdown: Color,    // DropdownMenu container
    val isDark: Boolean,
)

// ── Dark palette ──────────────────────────────────────────────────────────────

val DarkNetScopeColors = NetScopeColors(
    background = Color(0xFF0B0F1A),
    surface    = Color(0xFF111827),
    card       = Color(0xFF162032),
    cardAlt    = Color(0xFF0D1525),
    surface3   = Color(0xFF1C2A3E),
    border     = Color(0xFF1E2D45),
    border2    = Color(0xFF243550),
    accent     = Color(0xFF00C8F0),
    muted      = Color(0xFF6B7FA3),
    text2      = Color(0xFF8B9BB5),
    onSurface  = Color(0xFFE4EAF5),
    onCode     = Color(0xFFC8D4EE),
    code       = Color(0xFF0D1525),
    dropdown   = Color(0xFF162032),
    isDark     = true,
)

// ── Light palette ─────────────────────────────────────────────────────────────

val LightNetScopeColors = NetScopeColors(
    background = Color(0xFFF0F4F8),
    surface    = Color(0xFFFFFFFF),
    card       = Color(0xFFFFFFFF),
    cardAlt    = Color(0xFFEEF2F7),
    surface3   = Color(0xFFE6EBF4),
    border     = Color(0xFFDDE3EE),
    border2    = Color(0xFFCDD5E0),
    accent     = Color(0xFF0098C8),
    muted      = Color(0xFF8897AE),
    text2      = Color(0xFF6B7A8D),
    onSurface  = Color(0xFF0D1B2E),
    onCode     = Color(0xFF1D3557),
    code       = Color(0xFFEEF2F7),
    dropdown   = Color(0xFFFFFFFF),
    isDark     = false,
)

// ── Composition local ─────────────────────────────────────────────────────────

val LocalNetScopeColors = compositionLocalOf { LightNetScopeColors }

// ── Theme composable ──────────────────────────────────────────────────────────

@Composable
fun NetScopeTheme(
    isDark: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalNetScopeColors provides if (isDark) DarkNetScopeColors else LightNetScopeColors,
        content = content,
    )
}

// ── Token accessors ───────────────────────────────────────────────────────────

val NetScopeDarkBackground: Color @Composable get() = LocalNetScopeColors.current.background
val NetScopeSurface: Color         @Composable get() = LocalNetScopeColors.current.surface
val NetScopeCard: Color            @Composable get() = LocalNetScopeColors.current.card
val NetScopeCardAlt: Color         @Composable get() = LocalNetScopeColors.current.cardAlt
val NetScopeSurface3: Color        @Composable get() = LocalNetScopeColors.current.surface3
val NetScopeBorder: Color          @Composable get() = LocalNetScopeColors.current.border
val NetScopeBorder2: Color         @Composable get() = LocalNetScopeColors.current.border2
val NetScopeAccent: Color          @Composable get() = LocalNetScopeColors.current.accent
val NetScopeMuted: Color           @Composable get() = LocalNetScopeColors.current.muted
val NetScopeText2: Color           @Composable get() = LocalNetScopeColors.current.text2
val NetScopeOnSurface: Color       @Composable get() = LocalNetScopeColors.current.onSurface
val NetScopeOnCode: Color          @Composable get() = LocalNetScopeColors.current.onCode
val NetScopeCode: Color            @Composable get() = LocalNetScopeColors.current.code
val NetScopeDropdown: Color        @Composable get() = LocalNetScopeColors.current.dropdown

// ── Semantic colors (same across themes) ──────────────────────────────────────

val ColorSuccess  = Color(0xFF00D98A)  // Green  — 2xx, live dot
val ColorAmber    = Color(0xFFF5A623)  // Amber  — 3xx, 4xx, slow 300ms–1s
val ColorWarning  = ColorAmber         // alias
val ColorError    = Color(0xFFF04E6A)  // Red    — 5xx, destructive
val ColorRedirect = ColorAmber         // 3xx = Amber per design spec
val ColorPending  = Color(0xFF6B7FA3)  // muted grey
val ColorPurple   = Color(0xFF9B7FE8)  // Purple — POST method, detail accents
val ColorPink     = Color(0xFFD968A8)  // Pink   — AI layer, special features

fun NetTransaction.StatusClass.color() = when (this) {
    NetTransaction.StatusClass.SUCCESS      -> ColorSuccess
    NetTransaction.StatusClass.REDIRECT     -> ColorAmber
    NetTransaction.StatusClass.CLIENT_ERROR -> ColorAmber
    NetTransaction.StatusClass.SERVER_ERROR -> ColorError
    NetTransaction.StatusClass.ERROR        -> ColorError
    NetTransaction.StatusClass.PENDING      -> ColorPending
}

fun String.methodColor() = when (this.uppercase()) {
    "GET"    -> Color(0xFF00C8F0)  // Cyan
    "POST"   -> Color(0xFF9B7FE8)  // Purple
    "PUT"    -> Color(0xFFF5A623)  // Amber
    "PATCH"  -> Color(0xFF00D98A)  // Green
    "DELETE" -> Color(0xFFF04E6A)  // Red
    else     -> Color(0xFF6B7FA3)  // Muted (ANY / unknown)
}

/** statusColor for a raw HTTP code — used by badges and the status dot in the editor. */
fun statusColor(code: Int) = when (code) {
    in 200..299 -> ColorSuccess
    in 300..399 -> ColorAmber
    in 400..499 -> ColorAmber
    else        -> ColorError
}
