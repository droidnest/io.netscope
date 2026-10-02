package io.netscope.interceptor

import io.netscope.NetScopeConfig
import io.netscope.model.NetTransaction
import kotlinx.coroutines.Dispatchers
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSink
import okio.Pipe
import okio.Sink
import okio.buffer
import okio.gzip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class NetScopeInterceptorTest {

    private val repo = FakeTransactionRepository()
    private val url = "https://example.com/api/items"

    // ── Harness ───────────────────────────────────────────────────────────────

    /** Bytes the "server" received from the request body. */
    private val serverReceived = AtomicLong(-1)

    private fun client(
        config: NetScopeConfig = NetScopeConfig(),
        respond: (Request) -> Response = { ok(it, "{}".toResponseBody(JSON)) },
    ): OkHttpClient {
        val terminal = Interceptor { chain ->
            val request = chain.request()
            request.body?.let { body ->
                val sink = Buffer()
                body.writeTo(sink)
                serverReceived.set(sink.size)
            }
            respond(request)
        }
        return OkHttpClient.Builder()
            .addInterceptor(NetScopeInterceptor(repo, config, ioDispatcher = Dispatchers.Unconfined))
            .addInterceptor(terminal)
            .build()
    }

    private fun ok(request: Request, body: ResponseBody, vararg headers: Pair<String, String>): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .apply { headers.forEach { (n, v) -> addHeader(n, v) } }
            .body(body)
            .build()

    private fun lastRecorded(): NetTransaction = repo.updated.last()

    // ── Request body ──────────────────────────────────────────────────────────

    @Test
    fun `one-shot request body is left for the server and not captured`() {
        val body = object : RequestBody() {
            var writes = 0
            override fun contentType(): MediaType = JSON
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                check(++writes == 1) { "one-shot body written twice" }
                sink.writeUtf8("""{"secret":true}""")
            }
        }

        client().newCall(Request.Builder().url(url).post(body).build()).execute().close()

        assertEquals(1, body.writes)
        assertEquals(15L, serverReceived.get())
        assertNull(repo.inserted.single().requestBody)
    }

    @Test
    fun `large upload is captured up to maxBodySize only`() {
        val upload = ByteArray(5 * 1024 * 1024) { 'a'.code.toByte() }

        client(NetScopeConfig(maxBodySize = 1_000))
            .newCall(Request.Builder().url(url).post(upload.toRequestBody(TEXT)).build())
            .execute().close()

        val recorded = repo.inserted.single()
        assertEquals(1_000, recorded.requestBody!!.length)
        assertEquals(upload.size.toLong(), recorded.requestBodySize)
        assertEquals(upload.size.toLong(), serverReceived.get())
    }

    // ── Redaction ─────────────────────────────────────────────────────────────

    @Test
    fun `Authorization is redacted when NetScopeConfig is built directly`() {
        val config = NetScopeConfig(redactedHeaders = setOf("Authorization", "Set-Cookie"))
        val client = client(config) { ok(it, "{}".toResponseBody(JSON), "Set-Cookie" to "sid=42") }

        client.newCall(
            Request.Builder().url(url)
                .header("authorization", "Bearer secret-token")
                .header("X-Trace", "abc")
                .build()
        ).execute().close()

        val recorded = lastRecorded()
        val auth = recorded.requestHeaders.single { it.name.equals("Authorization", ignoreCase = true) }
        assertTrue(auth.isRedacted)
        assertEquals("██████", auth.value)
        assertEquals("abc", recorded.requestHeaders.single { it.name == "X-Trace" }.value)
        assertTrue(recorded.responseHeaders.single { it.name == "Set-Cookie" }.isRedacted)
    }

    // ── Failures ──────────────────────────────────────────────────────────────

    @Test
    fun `downstream runtime exception is recorded as error, not left pending`() {
        val client = client { throw IllegalStateException("boom") }

        assertThrows(IllegalStateException::class.java) {
            client.newCall(Request.Builder().url(url).build()).execute()
        }

        val recorded = lastRecorded()
        assertEquals("boom", recorded.error)
        assertEquals(NetTransaction.StatusClass.ERROR, recorded.statusClass)
    }

    // ── Response body ─────────────────────────────────────────────────────────

    @Test
    fun `app still reads the full response after a truncated capture`() {
        val payload = "x".repeat(10_000)
        val client = client(NetScopeConfig(maxBodySize = 100)) { ok(it, payload.toResponseBody(TEXT)) }

        val read = client.newCall(Request.Builder().url(url).build()).execute().use { it.body.string() }

        assertEquals(payload, read)
        assertEquals("x".repeat(100), lastRecorded().responseBody)
    }

    @Test(timeout = 5_000)
    fun `text event-stream response is not captured and does not block`() {
        // An open stream: if the interceptor waited for maxBodySize bytes it would hang forever.
        val pipe = Pipe(1024 * 1024)
        val events = pipe.sink.buffer()
        events.writeUtf8("data: hello\n\n").flush()
        val client = client {
            ok(it, pipe.source.buffer().asResponseBody(SSE), "Content-Type" to SSE.toString())
        }

        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            assertEquals("data: hello", response.body.source().readUtf8Line())
            events.close()
        }

        val recorded = lastRecorded()
        assertEquals(200, recorded.responseCode)
        assertNull(recorded.responseBody)
    }

    @Test
    fun `truncated gzip body yields partial decoded text instead of null`() {
        val random = Random(42)
        val text = buildString { repeat(20_000) { append("w").append(random.nextInt(100_000)).append(' ') } }
        val gzipped = Buffer().also { out -> (out as Sink).gzip().buffer().use { it.writeUtf8(text) } }.readByteArray()
        assertTrue("fixture must exceed the cap", gzipped.size > 2_000)

        val client = client(NetScopeConfig(maxBodySize = 2_000)) {
            ok(it, gzipped.toResponseBody(TEXT), "Content-Encoding" to "gzip")
        }
        client.newCall(Request.Builder().url(url).build()).execute().use { assertEquals(gzipped.size, it.body.bytes().size) }

        val captured = lastRecorded().responseBody
        assertNotNull(captured)
        assertTrue(captured!!.isNotEmpty())
        assertTrue(captured.length <= 2_000)
        assertTrue(text.startsWith(captured))
    }

    @Test
    fun `responseBodySize uses Content-Length bytes, not captured chars`() {
        val payload = "é".repeat(300) // 600 UTF-8 bytes
        val client = client(NetScopeConfig(maxBodySize = 100)) { ok(it, payload.toResponseBody(TEXT)) }

        client.newCall(Request.Builder().url(url).build()).execute().close()

        assertEquals(600L, lastRecorded().responseBodySize)
    }

    private companion object {
        val JSON = "application/json".toMediaType()
        val TEXT = "text/plain; charset=utf-8".toMediaType()
        val SSE = "text/event-stream".toMediaType()
    }
}
