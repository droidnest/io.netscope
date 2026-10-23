package io.netscope.data.db

import androidx.room.TypeConverters
import io.netscope.model.NetHeader

/**
 * Every column of [TransactionEntity] except the network-phase timing ones, for a partial
 * update: the timing listener writes those separately, possibly before the response update
 * lands, and a full-row update would overwrite them with nulls.
 */
@TypeConverters(HeaderListConverter::class)
data class TransactionWithoutTiming(
    val id: Long,

    // Timing
    val requestDate: Long,
    val responseDate: Long?,
    val durationMs: Long?,

    // Request
    val method: String,
    val scheme: String,
    val host: String,
    val path: String,
    val query: String?,
    val requestHeaders: List<NetHeader>,
    val requestBody: String?,
    val requestContentType: String?,
    val requestBodySize: Long,

    // Response
    val responseCode: Int?,
    val responseMessage: String?,
    val responseHeaders: List<NetHeader>,
    val responseBody: String?,
    val responseContentType: String?,
    val responseBodySize: Long,

    // Error
    val error: String?,
)
