package io.netscope.model

import io.netscope.NotificationStyle

/** The settings the NetScope Settings screen shows: what the library currently runs with. */
data class Settings(
    /** Maximum number of transactions kept. */
    val maxTransactions: Int,
    /** Maximum body size captured, in bytes. */
    val maxBodySize: Long,
    /** Whether bodies of non-text responses are read too. */
    val alwaysReadResponseBody: Boolean,
    /** Lower-case names of the headers whose values are redacted. */
    val redactedHeaders: Set<String>,
    /** Whether shaking the device opens NetScope. */
    val shakeToOpen: Boolean,
    /** How much the status-bar notification shows. */
    val notificationStyle: NotificationStyle,
)
