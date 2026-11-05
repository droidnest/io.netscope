package io.netscope.model

/**
 * No-op stub matching the real [NetTransaction] in netscope-core. None is ever captured.
 */
data class NetTransaction(
    val id: Long = 0,

    // ── Timing ──────────────────────────────────────────────────────────────
    val requestDate: Long,           // epoch millis — when request was sent
    val responseDate: Long? = null,  // epoch millis — when response was fully received
    val durationMs: Long? = null,    // responseDate - requestDate

    // ── Network phase timing (null when connection was reused) ───────────
    val dnsMs: Long? = null,         // DNS lookup duration
    val connectMs: Long? = null,     // TCP connection duration
    val sslMs: Long? = null,         // TLS handshake duration
    val ttfbMs: Long? = null,        // time-to-first-byte (server processing)

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
        get() = StatusClass.of(error = error, responseCode = responseCode)

    val totalSize: Long get() = requestBodySize + responseBodySize

    enum class StatusClass {
        PENDING, SUCCESS, REDIRECT, CLIENT_ERROR, SERVER_ERROR, ERROR;

        companion object {
            /** The class of a transaction that failed with [error] or got [responseCode]. */
            fun of(error: String?, responseCode: Int?): StatusClass = when {
                error != null -> ERROR
                responseCode == null -> PENDING
                responseCode < 300 -> SUCCESS
                responseCode < 400 -> REDIRECT
                responseCode < 500 -> CLIENT_ERROR
                else -> SERVER_ERROR
            }
        }
    }
}

data class NetHeader(
    val name: String,
    val value: String,
    val isRedacted: Boolean = false,
)
