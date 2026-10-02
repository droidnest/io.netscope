package io.netscope.data.db

import io.netscope.model.NetHeader

/**
 * Partial-row update carrying only the response columns of a [TransactionEntity].
 *
 * Used with `@Update(entity = TransactionEntity::class)` so persisting a response never
 * overwrites the network-phase timing columns (dnsMs / connectMs / sslMs / ttfbMs), which
 * [TransactionDao.updateTiming] writes concurrently and in no guaranteed order.
 */
data class TransactionResponseUpdate(
    val id: Long,
    val responseDate: Long?,
    val durationMs: Long?,
    val responseCode: Int?,
    val responseMessage: String?,
    val responseHeaders: List<NetHeader>,
    val responseBody: String?,
    val responseContentType: String?,
    val responseBodySize: Long,
    val error: String?,
)
