package io.netscope.model

import io.netscope.NotificationStyle

/** One setting the user changed: applied at once and saved for the next launches. */
sealed interface SettingChange {

    /** New [Settings.maxTransactions]. */
    data class MaxTransactions(val count: Int) : SettingChange

    /** New [Settings.maxBodySize]. */
    data class MaxBodySize(val bytes: Long) : SettingChange

    /** New [Settings.alwaysReadResponseBody]. */
    data class AlwaysReadResponseBody(val isEnabled: Boolean) : SettingChange

    /** New [Settings.redactedHeaders]. */
    data class RedactedHeaders(val headers: Set<String>) : SettingChange

    /** New [Settings.shakeToOpen]. */
    data class ShakeToOpen(val isEnabled: Boolean) : SettingChange

    /** New [Settings.notificationStyle]. */
    data class Notifications(val style: NotificationStyle) : SettingChange
}
