package io.netscope.data.mapper

import io.netscope.data.db.TransactionEntity
import io.netscope.data.db.TransactionResponseUpdate
import io.netscope.model.NetTransaction

internal object TransactionMapper {

    fun TransactionEntity.toDomain(): NetTransaction = NetTransaction(
        id = id,
        requestDate = requestDate,
        responseDate = responseDate,
        durationMs = durationMs,
        dnsMs = dnsMs,
        connectMs = connectMs,
        sslMs = sslMs,
        ttfbMs = ttfbMs,
        method = method,
        scheme = scheme,
        host = host,
        path = path,
        query = query,
        requestHeaders = requestHeaders,
        requestBody = requestBody,
        requestContentType = requestContentType,
        requestBodySize = requestBodySize,
        responseCode = responseCode,
        responseMessage = responseMessage,
        responseHeaders = responseHeaders,
        responseBody = responseBody,
        responseContentType = responseContentType,
        responseBodySize = responseBodySize,
        error = error,
    )

    fun NetTransaction.toEntity(): TransactionEntity = TransactionEntity(
        id = id,
        requestDate = requestDate,
        responseDate = responseDate,
        durationMs = durationMs,
        dnsMs = dnsMs,
        connectMs = connectMs,
        sslMs = sslMs,
        ttfbMs = ttfbMs,
        method = method,
        scheme = scheme,
        host = host,
        path = path,
        query = query,
        requestHeaders = requestHeaders,
        requestBody = requestBody,
        requestContentType = requestContentType,
        requestBodySize = requestBodySize,
        responseCode = responseCode,
        responseMessage = responseMessage,
        responseHeaders = responseHeaders,
        responseBody = responseBody,
        responseContentType = responseContentType,
        responseBodySize = responseBodySize,
        error = error,
    )

    /** Response-only projection used by [io.netscope.data.db.TransactionDao.updateResponse]. */
    fun NetTransaction.toResponseUpdate(): TransactionResponseUpdate = TransactionResponseUpdate(
        id = id,
        responseDate = responseDate,
        durationMs = durationMs,
        responseCode = responseCode,
        responseMessage = responseMessage,
        responseHeaders = responseHeaders,
        responseBody = responseBody,
        responseContentType = responseContentType,
        responseBodySize = responseBodySize,
        error = error,
    )
}
