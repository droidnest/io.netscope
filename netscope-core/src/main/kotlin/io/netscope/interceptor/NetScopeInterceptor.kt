package io.netscope.interceptor

import io.netscope.NetScopeConfig
import io.netscope.domain.repository.TransactionRepository
import io.netscope.model.NetHeader
import io.netscope.model.NetTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.http.promisesBody
import okio.Buffer
import okio.GzipSource
import okio.Sink
import okio.Timeout
import okio.buffer
import okio.utf8Size
import java.io.EOFException

/**
 * OkHttp [Interceptor] that captures every request/response and stores it
 * in the NetScope database via [TransactionRepository].
 *
 * Works as both an application and network interceptor, but must be added
 * via [okhttp3.OkHttpClient.Builder.addInterceptor] (application interceptor)
 * to capture headers added by other interceptors. Add it **before**
 * [NetScopeMockInterceptor] so mocked responses are recorded too.
 *
 * Capture never changes what the app or the server sees: request bodies are
 * copied through a size-capped sink (one-shot and duplex bodies are skipped),
 * and response bodies are peeked from the source buffer, never consumed.
 */
class NetScopeInterceptor internal constructor(
    private val repository: TransactionRepository,
    /** Replaced at runtime by [io.netscope.NetScope.updateConfig] from the UI thread. */
    @Volatile internal var config: NetScopeConfig,
    /** Optional timing factory — when set, the interceptor registers call→id mappings. */
    @Volatile internal var timingFactory: NetScopeTimingListenerFactory? = null,
    /** Dispatcher for repository writes; tests pass [Dispatchers.Unconfined]. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Interceptor {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    override fun intercept(chain: Interceptor.Chain): Response {
        // One snapshot per call so a concurrent updateConfig can't mix two configs.
        val config = config
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
            requestHeaders = request.headers.toNetHeaders(config.redactedLowercase),
            requestBody = request.readBodyAsString(config.maxBodySize),
            requestContentType = request.body?.contentType()?.toString(),
            requestBodySize = request.body?.contentLength() ?: 0L,
        )

        // Persist request immediately so it appears in UI before response
        val transactionId = saveSync(transaction)

        timingFactory?.register(chain.call(), transactionId)

        // ── Proceed — any failure (IOException or a downstream RuntimeException)
        //    is recorded so the row never stays PENDING forever ──────────────
        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            saveAsync(
                transaction.copy(
                    id = transactionId,
                    responseDate = System.currentTimeMillis(),
                    error = e.message ?: e.javaClass.simpleName,
                )
            )
            throw e
        }

        // ── Capture response ─────────────────────────────────────────────
        val responseDate = System.currentTimeMillis()
        val responseBodyString = if (response.promisesBody()) {
            response.readBodyAsString(config.maxBodySize, config.alwaysReadResponseBody)
        } else null
        val contentLength = response.body.contentLength()

        saveAsync(
            transaction.copy(
                id = transactionId,
                responseDate = responseDate,
                durationMs = responseDate - requestDate,
                responseCode = response.code,
                responseMessage = response.message,
                responseHeaders = response.headers.toNetHeaders(config.redactedLowercase),
                responseBody = responseBodyString,
                responseContentType = response.body.contentType()?.toString(),
                // Bytes on the wire when known — never the length of the truncated capture.
                responseBodySize = if (contentLength >= 0) contentLength else responseBodyString?.utf8Size() ?: 0L,
            )
        )
        return response
    }

    // ─── Persistence helpers ──────────────────────────────────────────────────

    private fun saveSync(transaction: NetTransaction): Long =
        // runBlocking is intentional — the partial record must be inserted
        // before the network call returns so the UI reflects "pending".
        runBlocking(ioDispatcher) { repository.insert(transaction) }

    private fun saveAsync(transaction: NetTransaction) {
        scope.launch { repository.update(transaction) }
    }

    // ─── Extension helpers ────────────────────────────────────────────────────

    /** [redactedLowercase] must already be lower-cased (see [NetScopeConfig.redactedLowercase]). */
    private fun Headers.toNetHeaders(redactedLowercase: Set<String>): List<NetHeader> =
        map { (name, value) ->
            val redact = name.lowercase() in redactedLowercase
            NetHeader(
                name = name,
                value = if (redact) "██████" else value,
                isRedacted = redact,
            )
        }

    /**
     * Copies at most [maxBytes] of the request body. One-shot and duplex bodies are
     * skipped — writing them here would consume the bytes the server needs.
     */
    private fun Request.readBodyAsString(maxBytes: Long): String? {
        val body = body ?: return null
        if (body.isOneShot() || body.isDuplex()) return null
        return try {
            val captured = Buffer()
            CappedSink(captured, maxBytes).buffer().use { body.writeTo(it) }
            captured.readUtf8()
        } catch (_: Exception) {
            null
        }
    }

    private fun Response.readBodyAsString(maxBytes: Long, alwaysRead: Boolean): String? {
        val contentType = body.contentType()?.toString() ?: ""
        // SSE never "completes" — waiting for maxBytes would stall the stream for the app.
        if (contentType.contains("event-stream", ignoreCase = true)) return null

        val isTextType = contentType.contains("json", ignoreCase = true) ||
                contentType.contains("xml", ignoreCase = true) ||
                contentType.contains("text", ignoreCase = true) ||
                contentType.contains("html", ignoreCase = true) ||
                contentType.contains("form", ignoreCase = true)

        if (!isTextType && !alwaysRead) return null

        return try {
            // Peek: buffer up to maxBytes and copy them, leaving the source untouched for the app.
            val source = body.source()
            source.request(maxBytes)
            val raw = Buffer()
            source.buffer.copyTo(raw, 0, minOf(source.buffer.size, maxBytes))

            // Handle gzip-encoded responses
            val encoding = header("Content-Encoding") ?: ""
            val decoded = if (encoding.equals("gzip", ignoreCase = true)) raw.gunzipCapped(maxBytes) else raw

            decoded.readUtf8(minOf(decoded.size, maxBytes))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Inflates at most ~[max] bytes. The input may be a truncated prefix of the stream, so
     * a premature EOF is expected — whatever was inflated before it is kept.
     */
    private fun Buffer.gunzipCapped(max: Long): Buffer {
        val out = Buffer()
        try {
            GzipSource(this).use { gzip ->
                while (out.size < max) {
                    if (gzip.read(out, GUNZIP_CHUNK) == -1L) break
                }
            }
        } catch (_: EOFException) {
            // Truncated input — keep partial output.
        }
        return out
    }

    private companion object {
        const val GUNZIP_CHUNK = 8_192L
    }
}

// ── Capped sink ───────────────────────────────────────────────────────────────

/**
 * [Sink] that keeps the first [cap] bytes in [target] and discards the rest,
 * so capturing a large upload never holds more than [cap] bytes in memory.
 */
private class CappedSink(private val target: Buffer, private val cap: Long) : Sink {

    override fun write(source: Buffer, byteCount: Long) {
        val keep = (cap - target.size).coerceIn(0L, byteCount)
        if (keep > 0) target.write(source, keep)
        source.skip(byteCount - keep)
    }

    override fun flush() = Unit

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() = Unit
}
