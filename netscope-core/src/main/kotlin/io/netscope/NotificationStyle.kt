package io.netscope

/**
 * Controls how much information is shown in the persistent status-bar notification.
 * Read at runtime by [io.netscope.notification.NetScopeNotificationManager].
 */
enum class NotificationStyle(val key: String, val label: String, val description: String) {
    /** Notification is hidden entirely. */
    NONE    ("none",     "Off",      "No notification shown"),
    /** Shows only the total request count. */
    MINIMAL ("minimal",  "Minimal",  "Live request counter only"),
    /** Shows request count, error count, and last response code. */
    DETAILED("detailed", "Detailed", "Counter · errors · last code");

    companion object {
        /** Returns [DETAILED] for unknown/null keys. */
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: DETAILED
    }
}
