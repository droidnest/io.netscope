package io.netscope.ui.preferences

import android.content.Context
import androidx.core.content.edit
import io.netscope.NetScopeSettings
import io.netscope.NotificationStyle

/**
 * Thin SharedPreferences wrapper that persists NetScope **interface** settings
 * (shakeToOpen, notificationStyle, themeMode).
 *
 * Capture & privacy settings are owned by core ([NetScopeSettings]) so they are
 * re-applied by `NetScope.interceptor(...)` on every launch. The capture properties
 * below are kept only for source compatibility and delegate to [NetScopeSettings].
 */
class NetScopePreferences(context: Context) {

    private val prefs    = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val settings = NetScopeSettings(context)

    // ── Capture (delegated to core) ───────────────────────────────────────────

    @Deprecated("Owned by core.", ReplaceWith("NetScopeSettings(context).maxTransactions", "io.netscope.NetScopeSettings"))
    var maxTransactions: Int
        get() = settings.maxTransactions
        set(v) { settings.maxTransactions = v }

    @Deprecated("Owned by core.", ReplaceWith("NetScopeSettings(context).maxBodySizeBytes", "io.netscope.NetScopeSettings"))
    var maxBodySizeBytes: Long
        get() = settings.maxBodySizeBytes
        set(v) { settings.maxBodySizeBytes = v }

    @Deprecated("Owned by core.", ReplaceWith("NetScopeSettings(context).alwaysReadResponseBody", "io.netscope.NetScopeSettings"))
    var alwaysReadResponseBody: Boolean
        get() = settings.alwaysReadResponseBody
        set(v) { settings.alwaysReadResponseBody = v }

    // ── Privacy (delegated to core) ───────────────────────────────────────────

    @Deprecated("Owned by core.", ReplaceWith("NetScopeSettings(context).redactedHeaders", "io.netscope.NetScopeSettings"))
    var redactedHeaders: Set<String>
        get() = settings.redactedHeaders
        set(v) { settings.redactedHeaders = v }

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
        // Same file as NetScopeSettings — interface keys live alongside the core-owned capture keys.
        private const val PREFS_NAME       = "netscope_settings"
        private const val KEY_SHAKE        = "shake_to_open"
        private const val KEY_NOTIF_STYLE  = "notification_style"
        private const val KEY_THEME        = "theme_mode"

        @Deprecated("Owned by core.", ReplaceWith("NetScopeSettings.DEFAULT_MAX_TX", "io.netscope.NetScopeSettings"))
        const val DEFAULT_MAX_TX   = NetScopeSettings.DEFAULT_MAX_TX

        @Deprecated("Owned by core.", ReplaceWith("NetScopeSettings.DEFAULT_MAX_BODY", "io.netscope.NetScopeSettings"))
        const val DEFAULT_MAX_BODY = NetScopeSettings.DEFAULT_MAX_BODY

        @Deprecated("Owned by core.", ReplaceWith("NetScopeSettings.DEFAULT_REDACTED", "io.netscope.NetScopeSettings"))
        val DEFAULT_REDACTED: Set<String> get() = NetScopeSettings.DEFAULT_REDACTED
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

// NotificationStyle is defined in netscope-core as io.netscope.NotificationStyle
// and re-exported here via the import above for backward compatibility.
