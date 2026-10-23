package io.netscope.ui.preferences

import android.content.Context
import io.netscope.NotificationStyle

/**
 * No-op stub matching the real [NetScopePreferences] in netscope-ui, so that release builds
 * compile code that reads a setting (`NetScopePreferences(context).shakeToOpen`). Reads return
 * the defaults; writes are ignored.
 */
class NetScopePreferences(context: Context) {

    var maxTransactions: Int
        get() = DEFAULT_MAX_TX
        set(v) = Unit

    var maxBodySizeBytes: Long
        get() = DEFAULT_MAX_BODY
        set(v) = Unit

    var alwaysReadResponseBody: Boolean
        get() = true
        set(v) = Unit

    var redactedHeaders: Set<String>
        get() = DEFAULT_REDACTED
        set(v) = Unit

    var shakeToOpen: Boolean
        get() = false
        set(v) = Unit

    var notificationStyle: NotificationStyle
        get() = NotificationStyle.NONE
        set(v) = Unit

    var themeMode: ThemeMode
        get() = ThemeMode.LIGHT
        set(v) = Unit

    companion object {
        const val DEFAULT_MAX_TX   = 500
        const val DEFAULT_MAX_BODY = 250_000L
        val DEFAULT_REDACTED: Set<String> = setOf("Authorization", "Cookie", "Set-Cookie")
    }
}

/** No-op stub matching the real [ThemeMode] in netscope-ui. */
enum class ThemeMode(val key: String, val label: String, val description: String) {
    LIGHT ("light",  "Light",  "Always use light theme"),
    DARK  ("dark",   "Dark",   "Always use dark theme"),
    SYSTEM("system", "System", "Follow device setting");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: LIGHT
    }
}
