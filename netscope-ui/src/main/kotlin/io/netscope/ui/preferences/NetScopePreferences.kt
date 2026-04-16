package io.netscope.ui.preferences

import android.content.Context
import androidx.core.content.edit

/**
 * Thin SharedPreferences wrapper that persists NetScope UI settings.
 *
 * Capture settings take effect on the next app launch.
 * Interface settings (shakeToOpen, notificationStyle, themeMode) are
 * applied immediately at runtime via callback.
 */
class NetScopePreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Capture ───────────────────────────────────────────────────────────────

    var maxTransactions: Int
        get() = prefs.getInt(KEY_MAX_TX, DEFAULT_MAX_TX)
        set(v) = prefs.edit { putInt(KEY_MAX_TX, v) }

    var maxBodySizeBytes: Long
        get() = prefs.getLong(KEY_MAX_BODY, DEFAULT_MAX_BODY)
        set(v) = prefs.edit { putLong(KEY_MAX_BODY, v) }

    var alwaysReadResponseBody: Boolean
        get() = prefs.getBoolean(KEY_ALWAYS_READ, true)
        set(v) = prefs.edit { putBoolean(KEY_ALWAYS_READ, v) }

    // ── Privacy ───────────────────────────────────────────────────────────────

    var redactedHeaders: Set<String>
        get() = prefs.getStringSet(KEY_REDACTED, DEFAULT_REDACTED) ?: DEFAULT_REDACTED
        set(v) = prefs.edit { putStringSet(KEY_REDACTED, v) }

    // ── Interface ─────────────────────────────────────────────────────────────

    var shakeToOpen: Boolean
        get() = prefs.getBoolean(KEY_SHAKE, true)
        set(v) = prefs.edit { putBoolean(KEY_SHAKE, v) }

    var notificationStyle: NotificationStyle
        get() = NotificationStyle.fromKey(prefs.getString(KEY_NOTIF_STYLE, null))
        set(v) = prefs.edit { putString(KEY_NOTIF_STYLE, v.key) }

    var themeMode: ThemeMode
        get() = ThemeMode.fromKey(prefs.getString(KEY_THEME, null))
        set(v) = prefs.edit { putString(KEY_THEME, v.key) }

    // ── Keys & defaults ───────────────────────────────────────────────────────

    companion object {
        private const val PREFS_NAME       = "netscope_settings"
        private const val KEY_MAX_TX       = "max_transactions"
        private const val KEY_MAX_BODY     = "max_body_size"
        private const val KEY_ALWAYS_READ  = "always_read_response_body"
        private const val KEY_REDACTED     = "redacted_headers"
        private const val KEY_SHAKE        = "shake_to_open"
        private const val KEY_NOTIF_STYLE  = "notification_style"
        private const val KEY_THEME        = "theme_mode"

        const val DEFAULT_MAX_TX   = 500
        const val DEFAULT_MAX_BODY = 250_000L
        val DEFAULT_REDACTED: Set<String> = setOf("Authorization", "Cookie", "Set-Cookie")
    }
}

// ── ThemeMode ─────────────────────────────────────────────────────────────────

enum class ThemeMode(val key: String, val label: String, val description: String) {
    LIGHT ("light",  "Light",  "Always use light theme"),
    DARK  ("dark",   "Dark",   "Always use dark theme"),
    SYSTEM("system", "System", "Follow device setting");

    companion object {
        /** Default is [LIGHT] — returns [LIGHT] for unknown/null keys. */
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: LIGHT
    }
}

// ── NotificationStyle ─────────────────────────────────────────────────────────

enum class NotificationStyle(val key: String, val label: String, val description: String) {
    NONE    ("none",     "Off",      "No notification shown"),
    MINIMAL ("minimal",  "Minimal",  "Live request counter only"),
    DETAILED("detailed", "Detailed", "Counter · errors · avg time");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: DETAILED
    }
}
