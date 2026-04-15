package io.netscope.ui.preferences

import android.content.Context
import androidx.core.content.edit

/**
 * Thin SharedPreferences wrapper that persists NetScope UI settings.
 *
 * Values here are read at screen open and written on every user change.
 * Capture settings (maxTransactions, maxBodySize, redactedHeaders,
 * alwaysReadResponseBody) take effect on the next app launch when the
 * developer passes them to [io.netscope.NetScope.interceptor].
 * Interface settings (showNotification) take effect immediately via
 * [io.netscope.NetScope].
 */
class NetScopePreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var maxTransactions: Int
        get() = prefs.getInt(KEY_MAX_TX, DEFAULT_MAX_TX)
        set(v) = prefs.edit { putInt(KEY_MAX_TX, v) }

    var maxBodySizeBytes: Long
        get() = prefs.getLong(KEY_MAX_BODY, DEFAULT_MAX_BODY)
        set(v) = prefs.edit { putLong(KEY_MAX_BODY, v) }

    var alwaysReadResponseBody: Boolean
        get() = prefs.getBoolean(KEY_ALWAYS_READ, true)
        set(v) = prefs.edit { putBoolean(KEY_ALWAYS_READ, v) }

    var showNotification: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION, true)
        set(v) = prefs.edit { putBoolean(KEY_NOTIFICATION, v) }

    var redactedHeaders: Set<String>
        get() = prefs.getStringSet(KEY_REDACTED, DEFAULT_REDACTED) ?: DEFAULT_REDACTED
        set(v) = prefs.edit { putStringSet(KEY_REDACTED, v) }

    companion object {
        private const val PREFS_NAME = "netscope_settings"

        private const val KEY_MAX_TX       = "max_transactions"
        private const val KEY_MAX_BODY     = "max_body_size"
        private const val KEY_ALWAYS_READ  = "always_read_response_body"
        private const val KEY_NOTIFICATION = "show_notification"
        private const val KEY_REDACTED     = "redacted_headers"

        const val DEFAULT_MAX_TX   = 500
        const val DEFAULT_MAX_BODY = 250_000L
        val DEFAULT_REDACTED: Set<String> = setOf("Authorization", "Cookie", "Set-Cookie")
    }
}
