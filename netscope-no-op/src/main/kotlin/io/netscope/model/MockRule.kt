package io.netscope.model

import java.util.UUID

/**
 * No-op stub matching the real [MockRule] in netscope-core, so that code building rules for
 * [io.netscope.NetScope.addMockRule] compiles in release builds. No rule is ever applied.
 */
data class MockRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val urlPattern: String,
    val matchType: MatchType = MatchType.CONTAINS,
    val method: String? = null,
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

    fun matches(url: String, requestMethod: String): Boolean = false
}
