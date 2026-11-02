package io.netscope.model

import io.netscope.NotificationStyle

/** No-op stub matching the real [Settings] in netscope-core. */
data class Settings(
    val maxTransactions: Int,
    val maxBodySize: Long,
    val alwaysReadResponseBody: Boolean,
    val redactedHeaders: Set<String>,
    val shakeToOpen: Boolean,
    val notificationStyle: NotificationStyle,
    val lockedRedactedHeaders: Set<String> = emptySet(),
)
