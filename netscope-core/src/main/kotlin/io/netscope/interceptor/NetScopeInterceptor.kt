package io.netscope.interceptor

import io.netscope.NetScopeConfig
import io.netscope.domain.repository.TransactionRepository
import io.netscope.model.NetHeader
import io.netscope.model.NetTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.internal.http.promisesBody
import okio.buffer
import java.io.IOException

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
    internal var config: NetScopeConfig,
    /** Optional timing factory — when set, the interceptor registers call→id mappings. */
    internal var timingFactory: NetScopeTimingListenerFactory? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Interceptor {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

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

        timingFactory?.register(chain.call(), transactionId)

        return try {
            val response = chain.proceed(request)
            val responseDate = System.currentTimeMillis()

            val completed = transaction.copy(
                id = transactionId,
                responseDate = responseDate,
                durationMs = responseDate - requestDate,
                responseCode = response.code,
                responseMessage = response.message,
                responseHeaders = response.headers.toNetHeaders(config.redactedHeaders),
                responseContentType = response.body?.contentType()?.toString(),
            )

            val saved = saveAsync(completed)
            if (response.promisesBody() && response.isCaptured(config.alwaysReadResponseBody)) {
                response.capturingBody(completed = completed, saved = saved)
            } else {
                response
            }
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
        kotlinx.coroutines.runBlocking(ioDispatcher) {
            id = repository.insert(transaction)
        }
        return id
    }

    private fun saveAsync(transaction: NetTransaction): Job =
        scope.launch { repository.update(transaction) }

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
        // Reading these would use up the only copy of the body, or wait for the server
        if (body.isOneShot() || body.isDuplex()) return null
        return try {
            captureUpTo(maxBytes = maxBytes) { sink -> body.writeTo(sink) }.readUtf8()
        } catch (_: Exception) {
            null
        }
    }

    private fun Response.isCaptured(alwaysRead: Boolean): Boolean {
        val contentType = body?.contentType()?.toString() ?: ""
        val isTextType = contentType.contains("json", ignoreCase = true) ||
                contentType.contains("xml", ignoreCase = true) ||
                contentType.contains("text", ignoreCase = true) ||
                contentType.contains("html", ignoreCase = true) ||
                contentType.contains("form", ignoreCase = true)
        return isTextType || alwaysRead
    }

    /**
     * This response with a body that records what the app reads from it. Reading ahead here would
     * hold the response back until [NetScopeConfig.maxBodySize] bytes arrive, forever on a stream.
     */
    private fun Response.capturingBody(completed: NetTransaction, saved: Job): Response {
        val original = body ?: return this
        val maxBytes = config.maxBodySize
        val isGzip = header("Content-Encoding").equals("gzip", ignoreCase = true)
        val capturing = CapturingSource(
            delegate = original.source(),
            maxBytes = maxBytes,
        ) { captured, totalBytes ->
            scope.launch {
                saved.join()
                val decoded = if (isGzip) captured.gunzipped(maxBytes = maxBytes) else captured
                repository.update(
                    completed.copy(
                        responseBody = decoded.readUtf8(minOf(decoded.size, maxBytes)),
                        responseBodySize = totalBytes,
                    ),
                )
            }
        }
        return newBuilder()
            .body(capturing.buffer().asResponseBody(original.contentType(), original.contentLength()))
            .build()
    }
}
