package io.netscope.data.mapper

import io.netscope.data.db.TransactionEntity
import io.netscope.data.db.TransactionWithoutTiming
import io.netscope.model.NetTransaction

internal object TransactionMapper {

    /**
     * Most UTF-8 bytes one body keeps in the database. A row has to fit in Android's 2 MB
     * CursorWindow with its two bodies and headers, or reading it throws.
     */
    const val MAX_STORED_BODY_BYTES = 800_000

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
        requestBody = requestBody?.storable(),
        requestContentType = requestContentType,
        requestBodySize = requestBodySize,
        responseCode = responseCode,
        responseMessage = responseMessage,
        responseHeaders = responseHeaders,
        responseBody = responseBody?.storable(),
        responseContentType = responseContentType,
        responseBodySize = responseBodySize,
        error = error,
    )

    fun NetTransaction.toEntityWithoutTiming(): TransactionWithoutTiming = TransactionWithoutTiming(
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
        requestBody = requestBody?.storable(),
        requestContentType = requestContentType,
        requestBodySize = requestBodySize,
        responseCode = responseCode,
        responseMessage = responseMessage,
        responseHeaders = responseHeaders,
        responseBody = responseBody?.storable(),
        responseContentType = responseContentType,
        responseBodySize = responseBodySize,
        error = error,
    )

    /** This body, cut on a character boundary at [MAX_STORED_BODY_BYTES] of UTF-8. */
    private fun String.storable(): String {
        if (length.toLong() * MAX_UTF8_BYTES_PER_CHAR <= MAX_STORED_BODY_BYTES) return this
        var bytes = 0
        forEachIndexed { index, char ->
            bytes += char.utf8Bytes()
            if (bytes > MAX_STORED_BODY_BYTES) {
                return substring(0, if (index > 0 && this[index - 1].isHighSurrogate()) index - 1 else index)
            }
        }
        return this
    }

    private fun Char.utf8Bytes(): Int = when {
        code < 0x80 -> 1
        code < 0x800 || isSurrogate() -> 2
        else -> MAX_UTF8_BYTES_PER_CHAR
    }

    private const val MAX_UTF8_BYTES_PER_CHAR = 3
}
