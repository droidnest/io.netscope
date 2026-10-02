package io.netscope.model

import java.util.UUID

// Mirror of netscope-core's class (same FQN) so host code using it compiles against the
// no-op artifact. Never on the same classpath as core — keep the two in sync.
data class MockRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val urlPattern: String,
    val matchType: MatchType = MatchType.CONTAINS,
    val method: String? = null,       // null → match any HTTP method
    val statusCode: Int = 200,
    val contentType: String = "application/json",
    val responseBody: String = "{}",
    val delayMs: Long = 0L,
    val isEnabled: Boolean = true,
) {
    enum class MatchType(val label: String) {
        CONTAINS("Contains"),
        EXACT("Exact"),
        REGEX("Regex"),
    }

    fun matches(url: String, requestMethod: String): Boolean {
        if (method != null && !method.equals(requestMethod, ignoreCase = true)) return false
        return when (matchType) {
            MatchType.CONTAINS -> url.contains(urlPattern, ignoreCase = true)
            MatchType.EXACT    -> url.equals(urlPattern, ignoreCase = true)
            MatchType.REGEX    -> runCatching { url.matches(Regex(urlPattern)) }.getOrDefault(false)
        }
    }
}
