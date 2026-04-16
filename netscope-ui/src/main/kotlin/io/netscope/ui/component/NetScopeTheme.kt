package io.netscope.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import io.netscope.model.NetTransaction

// ── Color token data class ─────────────────────────────────────────────────────

data class NetScopeColors(
    val background: Color,  // Screen/scaffold background
    val surface: Color,     // TopAppBar, URL strip
    val card: Color,        // Section cards, transaction rows
    val cardAlt: Color,     // Unselected chip backgrounds, input fields
    val border: Color,      // Dividers, stroke borders
    val accent: Color,      // Cyan highlight
    val muted: Color,       // Secondary text, icons
    val onSurface: Color,   // Primary text on any surface
    val onCode: Color,      // Monospace path / code text
    val code: Color,        // Code block background
    val dropdown: Color,    // DropdownMenu container
    val isDark: Boolean,
)

// ── Dark palette ──────────────────────────────────────────────────────────────

val DarkNetScopeColors = NetScopeColors(
    background = Color(0xFF0A0E1A),
    surface    = Color(0xFF111827),
    card       = Color(0xFF1A2235),
    cardAlt    = Color(0xFF0F1929),
    border     = Color(0xFF1E2D45),
    accent     = Color(0xFF00D4FF),
    muted      = Color(0xFF6B7FA3),
    onSurface  = Color.White,
    onCode     = Color(0xFFCDD6F4),
    code       = Color(0xFF060A14),
    dropdown   = Color(0xFF1A2235),
    isDark     = true,
)

// ── Light palette ─────────────────────────────────────────────────────────────

val LightNetScopeColors = NetScopeColors(
    background = Color(0xFFF0F4F8),
    surface    = Color(0xFFFFFFFF),
    card       = Color(0xFFFFFFFF),
    cardAlt    = Color(0xFFEEF2F7),
    border     = Color(0xFFDDE3EE),
    accent     = Color(0xFF0098C8),
    muted      = Color(0xFF8897AE),
    onSurface  = Color(0xFF0D1B2E),
    onCode     = Color(0xFF1D3557),
    code       = Color(0xFFF0F4F8),
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

// ── Token accessors — @Composable property getters ────────────────────────────
// These replace the old plain-val constants. Safe to use anywhere inside
// a @Composable function. The name surface deliberately shadows the old
// NetScopeDarkBackground so call-sites need no changes.

val NetScopeDarkBackground: Color @Composable get() = LocalNetScopeColors.current.background
val NetScopeSurface: Color         @Composable get() = LocalNetScopeColors.current.surface
val NetScopeCard: Color            @Composable get() = LocalNetScopeColors.current.card
val NetScopeCardAlt: Color         @Composable get() = LocalNetScopeColors.current.cardAlt
val NetScopeBorder: Color          @Composable get() = LocalNetScopeColors.current.border
val NetScopeAccent: Color          @Composable get() = LocalNetScopeColors.current.accent
val NetScopeMuted: Color           @Composable get() = LocalNetScopeColors.current.muted
val NetScopeOnSurface: Color       @Composable get() = LocalNetScopeColors.current.onSurface
val NetScopeOnCode: Color          @Composable get() = LocalNetScopeColors.current.onCode
val NetScopeCode: Color            @Composable get() = LocalNetScopeColors.current.code
val NetScopeDropdown: Color        @Composable get() = LocalNetScopeColors.current.dropdown

// ── Semantic status colors (same across themes) ───────────────────────────────

val ColorSuccess  = Color(0xFF00E5A0)
val ColorWarning  = Color(0xFFFFB547)
val ColorError    = Color(0xFFFF4D6A)
val ColorRedirect = Color(0xFF60A5FA)
val ColorPending  = Color(0xFF94A3B8)

fun NetTransaction.StatusClass.color() = when (this) {
    NetTransaction.StatusClass.SUCCESS      -> ColorSuccess
    NetTransaction.StatusClass.REDIRECT     -> ColorRedirect
    NetTransaction.StatusClass.CLIENT_ERROR -> ColorWarning
    NetTransaction.StatusClass.SERVER_ERROR -> ColorError
    NetTransaction.StatusClass.ERROR        -> ColorError
    NetTransaction.StatusClass.PENDING      -> ColorPending
}

fun String.methodColor() = when (this.uppercase()) {
    "GET"    -> Color(0xFF38BDF8)
    "POST"   -> Color(0xFFA78BFA)
    "PUT"    -> Color(0xFFFBBF24)
    "DELETE" -> Color(0xFFF87171)
    "PATCH"  -> Color(0xFF34D399)
    else     -> Color(0xFF94A3B8)
}
