package io.netscope

// Mirror of netscope-core's class (same FQN) so host code using it compiles against the
// no-op artifact. Never on the same classpath as core — keep the two in sync.
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
)
