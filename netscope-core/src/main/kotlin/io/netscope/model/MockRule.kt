package io.netscope.model

import java.util.UUID

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

    // ── Compiled-regex cache ──────────────────────────────────────────────────
    // Plain nullable fields rather than `by lazy`: Gson instantiates rules without running
    // initializers, which would leave a lazy delegate null. @Transient keeps them out of the
    // persisted JSON; body properties are not part of equals/hashCode/copy/toString.
    // Racing threads may compile the same pattern twice — harmless.

    @Volatile @Transient private var compiledRegex: Regex? = null
    @Volatile @Transient private var regexInvalid: Boolean = false

    private fun regexOrNull(): Regex? {
        if (regexInvalid) return null
        compiledRegex?.let { return it }
        return runCatching { Regex(urlPattern) }
            .onSuccess { compiledRegex = it }
            .onFailure { regexInvalid = true }
            .getOrNull()
    }

    fun matches(url: String, requestMethod: String): Boolean {
        if (method != null && !method.equals(requestMethod, ignoreCase = true)) return false
        return when (matchType) {
            MatchType.CONTAINS -> url.contains(urlPattern, ignoreCase = true)
            MatchType.EXACT    -> url.equals(urlPattern, ignoreCase = true)
            MatchType.REGEX    -> regexOrNull()?.matches(url) ?: false
        }
    }
}
