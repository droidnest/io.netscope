package io.netscope

/**
 * No-op stub matching the real [NetScopeConfig] in netscope-core, so that code reading
 * [NetScope.config] or calling [NetScope.updateConfig] compiles in release builds.
 */
data class NetScopeConfig(
    val maxTransactions: Int = 500,
    val redactedHeaders: Set<String> = setOf("Authorization", "Cookie", "Set-Cookie"),
    val maxBodySize: Long = 250_000L,
    val showNotification: Boolean = true,
    val alwaysReadResponseBody: Boolean = true,
)
