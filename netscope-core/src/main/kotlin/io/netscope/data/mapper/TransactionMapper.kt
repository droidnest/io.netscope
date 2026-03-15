package io.netscope.data.mapper

import io.netscope.data.db.TransactionEntity
import io.netscope.model.NetTransaction

internal object TransactionMapper {

    fun TransactionEntity.toDomain(): NetTransaction = NetTransaction(
        id = id,
        requestDate = requestDate,
        responseDate = responseDate,
        durationMs = durationMs,
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
}
