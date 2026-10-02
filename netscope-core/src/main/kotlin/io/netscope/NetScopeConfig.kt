package io.netscope

/**
 * Configuration for NetScope.
 * Build via [NetScope.interceptor] DSL or instantiate directly.
 */
data class NetScopeConfig(
    /** Maximum number of transactions to retain in the database. Oldest are pruned. */
    val maxTransactions: Int = 500,

    /** Headers whose values will be replaced with "██████" in the UI. */
    val redactedHeaders: Set<String> = setOf("Authorization", "Cookie", "Set-Cookie"),

    /** Maximum body size (bytes) captured per request/response. Larger bodies are truncated. */
    val maxBodySize: Long = 250_000L,

    /** Show a persistent notification with live request stats. */
    val showNotification: Boolean = true,

    /** Always read the response body even for non-text content types. */
    val alwaysReadResponseBody: Boolean = true,
) {
    /**
     * [redactedHeaders] lower-cased for case-insensitive matching (HTTP header names are
     * case-insensitive). Derived in the body, so it is recomputed on every `copy()` and is
     * correct however the config was built — DSL, direct constructor or `updateConfig`.
     */
    internal val redactedLowercase: Set<String> = redactedHeaders.mapTo(HashSet()) { it.lowercase() }
}
