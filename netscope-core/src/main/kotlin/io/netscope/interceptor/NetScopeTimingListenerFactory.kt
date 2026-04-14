package io.netscope.interceptor

import io.netscope.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap

/**
 * OkHttp [EventListener.Factory] that captures per-phase network timing
 * (DNS lookup, TCP connect, TLS handshake, time-to-first-byte) and writes
 * it to the NetScope repository once the call completes.
 *
 * **Usage** — add to your `OkHttpClient` alongside the `NetScopeInterceptor`:
 * ```kotlin
 * val timingFactory = NetScope.timingListenerFactory()
 * val client = OkHttpClient.Builder()
 *     .addInterceptor(NetScope.interceptor(context) { … })
 *     .eventListenerFactory(timingFactory)
 *     .build()
 * ```
 *
 * The interceptor and the factory share a [ConcurrentHashMap] that maps each
 * [Call] instance to its database transaction ID.  The interceptor registers
 * the ID immediately after the row is inserted; the listener reads it on
 * `callEnd` / `callFailed`.
 */
class NetScopeTimingListenerFactory internal constructor(
    private val repository: TransactionRepository,
) : EventListener.Factory {

    // Shared map: Call → DB row id.  Written by the interceptor, read by the listener.
    internal val pendingIds: ConcurrentHashMap<Call, Long> = ConcurrentHashMap()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun create(call: Call): EventListener =
        NetScopeTimingListener(call, pendingIds, scope, repository)
}

// ── Per-call listener ─────────────────────────────────────────────────────────

private class NetScopeTimingListener(
    private val call: Call,
    private val pendingIds: ConcurrentHashMap<Call, Long>,
    private val scope: CoroutineScope,
    private val repository: TransactionRepository,
) : EventListener() {

    private var dnsStart    = 0L
    private var connectStart = 0L
    private var sslStart    = 0L
    private var requestSent = 0L

    private var dnsMs:     Long? = null
    private var connectMs: Long? = null
    private var sslMs:     Long? = null
    private var ttfbMs:    Long? = null

    // ── DNS ──────────────────────────────────────────────────────────────────

    override fun dnsStart(call: Call, domainName: String) {
        dnsStart = now()
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        dnsMs = now() - dnsStart
    }

    // ── TCP connect ──────────────────────────────────────────────────────────

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        connectStart = now()
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
    ) {
        connectMs = now() - connectStart
    }

    // ── TLS handshake ────────────────────────────────────────────────────────

    override fun secureConnectStart(call: Call) {
        sslStart = now()
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        sslMs = now() - sslStart
    }

    // ── TTFB — from last byte of request body to first response byte ─────────

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        requestSent = now()
    }

    override fun requestHeadersEnd(call: Call, request: okhttp3.Request) {
        // For requests without a body, use headers-sent as the baseline.
        if (requestSent == 0L) requestSent = now()
    }

    override fun responseHeadersStart(call: Call) {
        if (requestSent > 0L) ttfbMs = now() - requestSent
    }

    // ── Flush on call completion ──────────────────────────────────────────────

    override fun callEnd(call: Call) = flush()

    override fun callFailed(call: Call, ioe: IOException) = flush()

    // ─────────────────────────────────────────────────────────────────────────

    private fun flush() {
        val id = pendingIds.remove(call) ?: return
        if (dnsMs == null && connectMs == null && sslMs == null && ttfbMs == null) return
        scope.launch {
            repository.updateTiming(id, dnsMs, connectMs, sslMs, ttfbMs)
        }
    }

    private fun now() = System.currentTimeMillis()
}
