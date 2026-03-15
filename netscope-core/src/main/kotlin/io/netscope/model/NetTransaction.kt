package io.netscope.model

/**
 * Immutable snapshot of a single HTTP transaction (request + response pair).
 */
data class NetTransaction(
    val id: Long = 0,

    // ── Timing ──────────────────────────────────────────────────────────────
    val requestDate: Long,           // epoch millis — when request was sent
    val responseDate: Long? = null,  // epoch millis — when response was fully received
    val durationMs: Long? = null,    // responseDate - requestDate

    // ── Request ─────────────────────────────────────────────────────────────
    val method: String,
    val scheme: String,
    val host: String,
    val path: String,
    val query: String?,
    val requestHeaders: List<NetHeader>,
    val requestBody: String?,
    val requestContentType: String?,
    val requestBodySize: Long,

    // ── Response ────────────────────────────────────────────────────────────
    val responseCode: Int? = null,
    val responseMessage: String? = null,
    val responseHeaders: List<NetHeader> = emptyList(),
    val responseBody: String? = null,
    val responseContentType: String? = null,
    val responseBodySize: Long = 0L,

    // ── Meta ────────────────────────────────────────────────────────────────
    val error: String? = null,       // populated if request threw an IOException
) {
    val url: String get() = "$scheme://$host$path${if (query != null) "?$query" else ""}"

    val statusClass: StatusClass
        get() = when {
            error != null -> StatusClass.ERROR
            responseCode == null -> StatusClass.PENDING
            responseCode < 300 -> StatusClass.SUCCESS
            responseCode < 400 -> StatusClass.REDIRECT
            responseCode < 500 -> StatusClass.CLIENT_ERROR
            else -> StatusClass.SERVER_ERROR
        }

    val totalSize: Long get() = requestBodySize + responseBodySize

    enum class StatusClass { PENDING, SUCCESS, REDIRECT, CLIENT_ERROR, SERVER_ERROR, ERROR }
}

data class NetHeader(
    val name: String,
    val value: String,
    val isRedacted: Boolean = false,
)
