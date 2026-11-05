package io.netscope.data.local

import android.content.Context
import android.content.SharedPreferences
import io.netscope.NetScopeConfig
import io.netscope.NotificationStyle
import io.netscope.model.SettingChange

/**
 * Reads and writes the settings the NetScope Settings screen saves, so that what the user chose
 * there applies from the first request of the next launch, before the UI is ever opened.
 *
 * `NetScopePreferences` in netscope-ui, public for the host app, uses the same values: the file
 * name and the keys below must stay identical to its own.
 */
internal class SavedSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * [config] with every capture setting the user saved. Saved redacted headers are added to
     * the configured ones rather than replacing them: Settings never un-redacts a header the
     * app itself redacts.
     */
    fun applyTo(config: NetScopeConfig): NetScopeConfig = config.copy(
        maxTransactions        = prefs.intOrNull(KEY_MAX_TX) ?: config.maxTransactions,
        maxBodySize            = prefs.longOrNull(KEY_MAX_BODY) ?: config.maxBodySize,
        alwaysReadResponseBody = prefs.booleanOrNull(KEY_ALWAYS_READ) ?: config.alwaysReadResponseBody,
        redactedHeaders        = config.redactedHeaders + savedRedactedHeaders(),
    )

    /** The saved notification style, or null when the user never picked one. */
    val notificationStyle: NotificationStyle?
        get() = prefs.getString(KEY_NOTIF_STYLE, null)?.let { NotificationStyle.fromKey(it) }

    /** The saved shake-to-open choice, or null when the user never changed it. */
    val shakeToOpen: Boolean?
        get() = prefs.booleanOrNull(KEY_SHAKE)

    /** Saves [change] under the key `NetScopePreferences` reads and writes. */
    fun save(change: SettingChange) {
        val editor = prefs.edit()
        when (change) {
            is SettingChange.MaxTransactions        -> editor.putInt(KEY_MAX_TX, change.count)
            is SettingChange.MaxBodySize            -> editor.putLong(KEY_MAX_BODY, change.bytes)
            is SettingChange.AlwaysReadResponseBody -> editor.putBoolean(KEY_ALWAYS_READ, change.isEnabled)
            is SettingChange.RedactedHeaders        -> editor.putStringSet(KEY_REDACTED, change.headers)
            is SettingChange.ShakeToOpen            -> editor.putBoolean(KEY_SHAKE, change.isEnabled)
            is SettingChange.Notifications          -> editor.putString(KEY_NOTIF_STYLE, change.style.key)
        }
        editor.apply()
    }

    private fun savedRedactedHeaders(): Set<String> =
        prefs.getStringSet(KEY_REDACTED, null).orEmpty().map { it.lowercase() }.toSet()

    private fun SharedPreferences.intOrNull(key: String): Int? =
        if (contains(key)) getInt(key, 0) else null

    private fun SharedPreferences.longOrNull(key: String): Long? =
        if (contains(key)) getLong(key, 0L) else null

    private fun SharedPreferences.booleanOrNull(key: String): Boolean? =
        if (contains(key)) getBoolean(key, false) else null

    private companion object {
        // Same file and keys as NetScopePreferences (netscope-ui)
        const val PREFS_NAME      = "netscope_settings"
        const val KEY_MAX_TX      = "max_transactions"
        const val KEY_MAX_BODY    = "max_body_size"
        const val KEY_ALWAYS_READ = "always_read_response_body"
        const val KEY_REDACTED    = "redacted_headers"
        const val KEY_SHAKE       = "shake_to_open"
        const val KEY_NOTIF_STYLE = "notification_style"
    }
}
