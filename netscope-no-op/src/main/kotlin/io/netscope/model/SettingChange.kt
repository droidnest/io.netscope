package io.netscope.model

import io.netscope.NotificationStyle

/** No-op stub matching the real [SettingChange] in netscope-core. */
sealed interface SettingChange {

    data class MaxTransactions(val count: Int) : SettingChange

    data class MaxBodySize(val bytes: Long) : SettingChange

    data class AlwaysReadResponseBody(val isEnabled: Boolean) : SettingChange

    data class RedactedHeaders(val headers: Set<String>) : SettingChange

    data class ShakeToOpen(val isEnabled: Boolean) : SettingChange

    data class Notifications(val style: NotificationStyle) : SettingChange
}
