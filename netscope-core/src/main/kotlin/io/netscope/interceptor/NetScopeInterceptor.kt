package io.netscope.interceptor

import io.netscope.NetScopeConfig
import io.netscope.domain.repository.TransactionRepository
import io.netscope.model.NetHeader
import io.netscope.model.NetTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.http.promisesBody
import okio.Buffer
import okio.GzipSource
import java.io.IOException
import java.nio.charset.Charset

/**
 * OkHttp [Interceptor] that captures every request/response and stores it
 * in the NetScope database via [TransactionRepository].
 *
 * Works as both an application and network interceptor, but must be added
 * via [okhttp3.OkHttpClient.Builder.addInterceptor] (application interceptor)
 * to capture headers added by other interceptors.
 */
class NetScopeInterceptor internal constructor(
    private val repository: TransactionRepository,
    private val config: NetScopeConfig,
) : Interceptor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestDate = System.currentTimeMillis()

        // ── Build partial transaction (request only) ──────────────────────
        val transaction = NetTransaction(
            requestDate = requestDate,
            method = request.method,
            scheme = request.url.scheme,
            host = request.url.host,
            path = request.url.encodedPath,
            query = request.url.encodedQuery,
            requestHeaders = request.headers.toNetHeaders(config.redactedHeaders),
            requestBody = request.readBodyAsString(config.maxBodySize),
            requestContentType = request.body?.contentType()?.toString(),
            requestBodySize = request.body?.contentLength() ?: 0L,
        )

        // Persist request immediately so it appears in UI before response
        val transactionId = saveSync(transaction)

        return try {
            val response = chain.proceed(request)
            val responseDate = System.currentTimeMillis()

            val responseBodyString = if (response.promisesBody()) {
                response.readBodyAsString(config.maxBodySize, config.alwaysReadResponseBody)
            } else null

            val updated = transaction.copy(
                id = transactionId,
                responseDate = responseDate,
                durationMs = responseDate - requestDate,
                responseCode = response.code,
                responseMessage = response.message,
                responseHeaders = response.headers.toNetHeaders(config.redactedHeaders),
                responseBody = responseBodyString,
                responseContentType = response.body?.contentType()?.toString(),
                responseBodySize = responseBodyString?.length?.toLong() ?: 0L,
            )

            saveAsync(updated)
            response
        } catch (e: IOException) {
            val updated = transaction.copy(
                id = transactionId,
                responseDate = System.currentTimeMillis(),
                error = e.message ?: e.javaClass.simpleName,
            )
            saveAsync(updated)
            throw e
        }
    }

    // ─── Persistence helpers ──────────────────────────────────────────────────

    private fun saveSync(transaction: NetTransaction): Long {
        var id = 0L
        // We use runBlocking here intentionally — the partial record must be
        // inserted before the network call returns so the UI reflects "pending".
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            id = repository.insert(transaction)
        }
        return id
    }

    private fun saveAsync(transaction: NetTransaction) {
        scope.launch { repository.update(transaction) }
    }

    // ─── Extension helpers ────────────────────────────────────────────────────

    private fun Headers.toNetHeaders(redacted: Set<String>): List<NetHeader> =
        map { (name, value) ->
            NetHeader(
                name = name,
                value = if (name.lowercase() in redacted) "██████" else value,
                isRedacted = name.lowercase() in redacted,
            )
        }

    private fun Request.readBodyAsString(maxBytes: Long): String? {
        val body = body ?: return null
        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readString(minOf(buffer.size, maxBytes), Charset.forName("UTF-8"))
        } catch (_: Exception) {
            null
        }
    }

    private fun Response.readBodyAsString(maxBytes: Long, alwaysRead: Boolean): String? {
        val contentType = body?.contentType()?.toString() ?: ""
        val isTextType = contentType.contains("json", ignoreCase = true) ||
                contentType.contains("xml", ignoreCase = true) ||
                contentType.contains("text", ignoreCase = true) ||
                contentType.contains("html", ignoreCase = true) ||
                contentType.contains("form", ignoreCase = true)

        if (!isTextType && !alwaysRead) return null

        return try {
            val source = body?.source() ?: return null
            source.request(maxBytes)
            val buffer = source.buffer.clone()

            // Handle gzip-encoded responses
            val encoding = header("Content-Encoding") ?: ""
            val finalBuffer = if (encoding.equals("gzip", ignoreCase = true)) {
                Buffer().also { out -> GzipSource(buffer).use { gzip -> out.writeAll(gzip) } }
            } else {
                buffer
            }

            finalBuffer.readString(minOf(finalBuffer.size, maxBytes), Charset.forName("UTF-8"))
        } catch (_: Exception) {
            null
        }
    }
}
