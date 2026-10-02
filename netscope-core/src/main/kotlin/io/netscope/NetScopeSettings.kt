package io.netscope

import android.content.Context

/**
 * Persisted capture & privacy settings, owned by core so they are re-applied on every launch
 * (see [NetScope.interceptor]). The Settings UI writes through this class.
 */
class NetScopeSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var maxTransactions: Int
        get() = prefs.getInt(KEY_MAX_TX, DEFAULT_MAX_TX)
        set(v) = prefs.edit().putInt(KEY_MAX_TX, v).apply()

    var maxBodySizeBytes: Long
        get() = prefs.getLong(KEY_MAX_BODY, DEFAULT_MAX_BODY)
        set(v) = prefs.edit().putLong(KEY_MAX_BODY, v).apply()

    var alwaysReadResponseBody: Boolean
        get() = prefs.getBoolean(KEY_ALWAYS_READ, true)
        set(v) = prefs.edit().putBoolean(KEY_ALWAYS_READ, v).apply()

    var redactedHeaders: Set<String>
        get() = prefs.getStringSet(KEY_REDACTED, DEFAULT_REDACTED) ?: DEFAULT_REDACTED
        set(v) = prefs.edit().putStringSet(KEY_REDACTED, v.toSet()).apply()

    /** Persisted notification style, or null when the user never changed it. Written by the Settings UI. */
    internal val savedNotificationStyle: NotificationStyle?
        get() = prefs.getString(KEY_NOTIF_STYLE, null)?.let(NotificationStyle::fromKey)

    /**
     * Overlays values the user changed in Settings onto [base].
     * Redaction is a union: Settings can add redacted headers but never remove one the code declares.
     */
    internal fun applyTo(base: NetScopeConfig): NetScopeConfig = base.copy(
        maxTransactions = if (prefs.contains(KEY_MAX_TX)) maxTransactions else base.maxTransactions,
        maxBodySize = if (prefs.contains(KEY_MAX_BODY)) maxBodySizeBytes else base.maxBodySize,
        alwaysReadResponseBody = if (prefs.contains(KEY_ALWAYS_READ)) alwaysReadResponseBody else base.alwaysReadResponseBody,
        redactedHeaders = base.redactedHeaders + (if (prefs.contains(KEY_REDACTED)) redactedHeaders else emptySet()),
    )

    companion object {
        // Same file and keys the UI used before this class moved to core — existing user settings carry over.
        internal const val PREFS_NAME      = "netscope_settings"
        private const val KEY_MAX_TX       = "max_transactions"
        private const val KEY_MAX_BODY     = "max_body_size"
        private const val KEY_ALWAYS_READ  = "always_read_response_body"
        private const val KEY_REDACTED     = "redacted_headers"
        private const val KEY_NOTIF_STYLE  = "notification_style"

        const val DEFAULT_MAX_TX   = 500
        const val DEFAULT_MAX_BODY = 250_000L
        val DEFAULT_REDACTED: Set<String> = setOf("Authorization", "Cookie", "Set-Cookie")
    }
}
