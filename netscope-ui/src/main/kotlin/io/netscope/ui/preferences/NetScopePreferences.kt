package io.netscope.ui.preferences

import android.content.Context
import androidx.core.content.edit

/**
 * Thin SharedPreferences wrapper that persists NetScope UI settings.
 *
 * Capture settings (maxTransactions, maxBodySizeBytes, redactedHeaders,
 * alwaysReadResponseBody) take effect on the next app launch when the
 * developer passes them to [io.netscope.NetScope.interceptor].
 *
 * Interface settings (shakeToOpen, notificationStyle) can be applied
 * immediately at runtime.
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

    // ── Keys & defaults ───────────────────────────────────────────────────────

    companion object {
        private const val PREFS_NAME = "netscope_settings"

        private const val KEY_MAX_TX       = "max_transactions"
        private const val KEY_MAX_BODY     = "max_body_size"
        private const val KEY_ALWAYS_READ  = "always_read_response_body"
        private const val KEY_REDACTED     = "redacted_headers"
        private const val KEY_SHAKE        = "shake_to_open"
        private const val KEY_NOTIF_STYLE  = "notification_style"

        const val DEFAULT_MAX_TX   = 500
        const val DEFAULT_MAX_BODY = 250_000L
        val DEFAULT_REDACTED: Set<String> = setOf("Authorization", "Cookie", "Set-Cookie")
    }
}

/**
 * Notification display density shown in the system status bar.
 *
 * - [NONE]     — notification is hidden entirely.
 * - [MINIMAL]  — shows only the live request counter.
 * - [DETAILED] — shows counter + error count + average response time.
 */
enum class NotificationStyle(val key: String, val label: String, val description: String) {
    NONE    ("none",     "Off",      "No notification shown"),
    MINIMAL ("minimal",  "Minimal",  "Live request counter only"),
    DETAILED("detailed", "Detailed", "Counter · errors · avg time");

    companion object {
        fun fromKey(key: String?): NotificationStyle =
            entries.firstOrNull { it.key == key } ?: DETAILED
    }
}
