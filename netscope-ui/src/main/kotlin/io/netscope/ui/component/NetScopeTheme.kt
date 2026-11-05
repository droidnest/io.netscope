package io.netscope.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.netscope.model.NetTransaction

// ── Color token data class ─────────────────────────────────────────────────────

/** Code colours of a body, plain values so that a line can be coloured outside composition. */
@Immutable
data class SyntaxColors(
    val key: Color,
    val string: Color,
    val number: Color,
    val keyword: Color,
    val punctuation: Color,
    val plain: Color,
)

/**
 * Every colour that carries text passes WCAG AA, 4.5:1, on the surfaces it sits on, and a semantic
 * colour does on its own 15% tint too, which is how chips and badges use it. A semantic colour
 * therefore has one value per theme: what reads on a dark card is invisible on a white one.
 */
@Immutable
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
    val onAccent: Color,    // Content of a filled accent surface
    val success: Color,     // 2xx, fast requests
    val warning: Color,     // 3xx, 4xx, slow requests
    val error: Color,       // 5xx, failures, destructive actions
    val pending: Color,     // In-flight and disabled
    val purple: Color,      // POST, detail accents
    val pink: Color,        // TLS phase
    val cyan: Color,        // GET, TCP phase
    val syntax: SyntaxColors,
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
    muted      = Color(0xFF8DA0C2),
    text2      = Color(0xFFA3B2CB),
    onSurface  = Color(0xFFE4EAF5),
    onCode     = Color(0xFFC8D4EE),
    code       = Color(0xFF0D1525),
    dropdown   = Color(0xFF162032),
    onAccent   = Color(0xFF04222C),
    success    = Color(0xFF00D98A),
    warning    = Color(0xFFF5A623),
    error      = Color(0xFFFF8097),
    pending    = Color(0xFF95A7C8),
    purple     = Color(0xFFBBA6F7),
    pink       = Color(0xFFEA8CC2),
    cyan       = Color(0xFF00C8F0),
    syntax     = SyntaxColors(
        key         = Color(0xFF7DD3FC),
        string      = Color(0xFF86EFAC),
        number      = Color(0xFFFBBF24),
        keyword     = Color(0xFFBBA6F7),
        punctuation = Color(0xFF8DA0C2),
        plain       = Color(0xFFCDD6F4),
    ),
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
    accent     = Color(0xFF00739A),
    muted      = Color(0xFF5C6B82),
    text2      = Color(0xFF4A586C),
    onSurface  = Color(0xFF0D1B2E),
    onCode     = Color(0xFF1D3557),
    code       = Color(0xFFEEF2F7),
    dropdown   = Color(0xFFFFFFFF),
    onAccent   = Color(0xFFFFFFFF),
    success    = Color(0xFF00704A),
    warning    = Color(0xFF8F5400),
    error      = Color(0xFFBE1E3C),
    pending    = Color(0xFF536178),
    purple     = Color(0xFF6440C4),
    pink       = Color(0xFFA63277),
    cyan       = Color(0xFF006F8C),
    syntax     = SyntaxColors(
        key         = Color(0xFF0B5F99),
        string      = Color(0xFF0B6B3F),
        number      = Color(0xFF8F4A00),
        keyword     = Color(0xFF6440C4),
        punctuation = Color(0xFF5C6B82),
        plain       = Color(0xFF1D3557),
    ),
    isDark     = false,
)

// ── Composition local ─────────────────────────────────────────────────────────

// Static: rows read several colours each, and a theme changes far too rarely to track those reads
val LocalNetScopeColors = staticCompositionLocalOf { LightNetScopeColors }

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
val NetScopeOnAccent: Color        @Composable get() = LocalNetScopeColors.current.onAccent
val NetScopeSyntax: SyntaxColors   @Composable get() = LocalNetScopeColors.current.syntax

// ── Semantic colors ───────────────────────────────────────────────────────────

val ColorSuccess: Color  @Composable get() = LocalNetScopeColors.current.success
val ColorAmber: Color    @Composable get() = LocalNetScopeColors.current.warning
val ColorError: Color    @Composable get() = LocalNetScopeColors.current.error
val ColorPending: Color  @Composable get() = LocalNetScopeColors.current.pending
val ColorPurple: Color   @Composable get() = LocalNetScopeColors.current.purple
val ColorPink: Color     @Composable get() = LocalNetScopeColors.current.pink
val ColorCyan: Color     @Composable get() = LocalNetScopeColors.current.cyan

@Composable
fun NetTransaction.StatusClass.color(): Color = when (this) {
    NetTransaction.StatusClass.SUCCESS      -> ColorSuccess
    NetTransaction.StatusClass.REDIRECT     -> ColorAmber
    NetTransaction.StatusClass.CLIENT_ERROR -> ColorAmber
    NetTransaction.StatusClass.SERVER_ERROR -> ColorError
    NetTransaction.StatusClass.ERROR        -> ColorError
    NetTransaction.StatusClass.PENDING      -> ColorPending
}

@Composable
fun String.methodColor(): Color = when (this.uppercase()) {
    "GET"    -> ColorCyan
    "POST"   -> ColorPurple
    "PUT"    -> ColorAmber
    "PATCH"  -> ColorSuccess
    "DELETE" -> ColorError
    else     -> ColorPending
}

/** statusColor for a raw HTTP code — used by badges and the status dot in the editor. */
@Composable
fun statusColor(code: Int): Color = when (code) {
    in 200..299 -> ColorSuccess
    in 300..399 -> ColorAmber
    in 400..499 -> ColorAmber
    else        -> ColorError
}
