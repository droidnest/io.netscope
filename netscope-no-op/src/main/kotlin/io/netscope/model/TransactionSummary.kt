package io.netscope.model

/**
 * The columns of one transaction a list needs, without its bodies and headers: a row costs little
 * to read and to compare however large the traffic is.
 */
data class TransactionSummary(
    val id: Long,
    val requestDate: Long,
    val durationMs: Long?,
    val method: String,
    val host: String,
    val path: String,
    val query: String?,
    val requestBodySize: Long,
    val responseCode: Int?,
    val responseBodySize: Long,
    val error: String?,
) {
    val statusClass: NetTransaction.StatusClass
        get() = NetTransaction.StatusClass.of(error = error, responseCode = responseCode)

    val totalSize: Long get() = requestBodySize + responseBodySize
}
