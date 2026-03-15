package io.netscope.ui.component

import androidx.compose.ui.graphics.Color
import io.netscope.model.NetTransaction

// ── Color tokens ──────────────────────────────────────────────────────────────

val NetScopeDarkBackground = Color(0xFF0A0E1A)
val NetScopeSurface = Color(0xFF111827)
val NetScopeCard = Color(0xFF1A2235)
val NetScopeBorder = Color(0xFF1E2D45)
val NetScopeAccent = Color(0xFF00D4FF)
val NetScopeMuted = Color(0xFF6B7FA3)

val ColorSuccess = Color(0xFF00E5A0)
val ColorWarning = Color(0xFFFFB547)
val ColorError = Color(0xFFFF4D6A)
val ColorRedirect = Color(0xFF60A5FA)
val ColorPending = Color(0xFF94A3B8)

fun NetTransaction.StatusClass.color() = when (this) {
    NetTransaction.StatusClass.SUCCESS -> ColorSuccess
    NetTransaction.StatusClass.REDIRECT -> ColorRedirect
    NetTransaction.StatusClass.CLIENT_ERROR -> ColorWarning
    NetTransaction.StatusClass.SERVER_ERROR -> ColorError
    NetTransaction.StatusClass.ERROR -> ColorError
    NetTransaction.StatusClass.PENDING -> ColorPending
}

fun String.methodColor() = when (this.uppercase()) {
    "GET" -> Color(0xFF38BDF8)
    "POST" -> Color(0xFFA78BFA)
    "PUT" -> Color(0xFFFBBF24)
    "DELETE" -> Color(0xFFF87171)
    "PATCH" -> Color(0xFF34D399)
    else -> Color(0xFF94A3B8)
}
