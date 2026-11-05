package io.netscope.interceptor

import io.netscope.NetScopeConfig
import io.netscope.fake.FakeTransactionRepository
import io.netscope.fake.aChain
import io.netscope.fake.aRequest
import io.netscope.fake.aResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSink
import okio.GzipSink
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetScopeInterceptorTest {

    private val repository = FakeTransactionRepository()
    private val json = "application/json".toMediaType()

    private fun sut(config: NetScopeConfig = NetScopeConfig()) = NetScopeInterceptor(
        repository = repository,
        config = config,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    private val stored get() = repository.transactions.value.single()

    @Test
    fun `should return the response without reading its body when intercepting a stream`() {
        // Given
        val stream = CountingSource()
        val chain = aChain(request = aRequest()) {
            aResponse(request = it, body = stream.buffer().asResponseBody(json, -1L))
        }

        // When
        sut().intercept(chain)

        // Then
        assertEquals(0, stream.reads)
    }

    @Test
    fun `should record the status before the body is read`() {
        // Given
        val chain = aChain(request = aRequest()) {
            aResponse(request = it, body = CountingSource().buffer().asResponseBody(json, -1L))
        }

        // When
        sut().intercept(chain)

        // Then
        assertEquals(200, stored.responseCode)
        assertNull(stored.responseBody)
    }

    @Test
    fun `should record the body and still hand it to the app when the app reads it`() {
        // Given
        val chain = aChain(request = aRequest()) {
            aResponse(request = it, body = """{"id":1}""".toResponseBody(json))
        }

        // When
        val read = sut().intercept(chain).body.string()

        // Then
        assertEquals("""{"id":1}""", read)
        assertEquals("""{"id":1}""", stored.responseBody)
        assertEquals(8L, stored.responseBodySize)
    }

    @Test
    fun `should record the part read when the app closes the body early`() {
        // Given
        val chain = aChain(request = aRequest()) {
            aResponse(request = it, body = "abcdef".toResponseBody(json))
        }
        val response = sut().intercept(chain)

        // When
        response.body.source().readUtf8(3)
        response.close()

        // Then
        assertTrue(stored.responseBody.orEmpty().startsWith("abc"))
    }

    @Test
    fun `should keep only the size limit of the body when it is larger`() {
        // Given
        val chain = aChain(request = aRequest()) {
            aResponse(request = it, body = "x".repeat(100).toResponseBody(json))
        }

        // When
        val read = sut(config = NetScopeConfig(maxBodySize = 10L)).intercept(chain).body.string()

        // Then
        assertEquals(100, read.length)
        assertEquals("x".repeat(10), stored.responseBody)
        assertEquals(100L, stored.responseBodySize)
    }

    @Test
    fun `should inflate a gzip body when recording it`() {
        // Given
        val gzipped = Buffer().also { out -> GzipSink(out).buffer().use { it.writeUtf8("hello") } }
        val chain = aChain(request = aRequest()) {
            aResponse(
                request = it,
                body = gzipped.asResponseBody(json, gzipped.size),
                headers = headersOf("Content-Encoding", "gzip"),
            )
        }

        // When
        sut().intercept(chain).body.bytes()

        // Then
        assertEquals("hello", stored.responseBody)
    }

    @Test
    fun `should leave a one-shot request body unread when capturing`() {
        // Given
        val body = RecordingBody(oneShot = true)

        // When
        sut().intercept(aChain(request = aRequest(body = body)))

        // Then
        assertEquals(0, body.writes)
        assertNull(stored.requestBody)
    }

    @Test
    fun `should leave a duplex request body unread when capturing`() {
        // Given
        val body = RecordingBody(duplex = true)

        // When
        sut().intercept(aChain(request = aRequest(body = body)))

        // Then
        assertEquals(0, body.writes)
    }

    @Test
    fun `should stop reading a request body at the size limit`() {
        // Given
        val body = RecordingBody(chunks = 10_000)

        // When
        sut(config = NetScopeConfig(maxBodySize = 100L)).intercept(aChain(request = aRequest(body = body)))

        // Then
        assertEquals("x".repeat(100), stored.requestBody)
        assertFalse(body.isWrittenWhole)
    }

    private class CountingSource : Source {
        var reads = 0
        override fun read(sink: Buffer, byteCount: Long): Long {
            reads++
            return -1L
        }
        override fun timeout(): Timeout = Timeout.NONE
        override fun close() = Unit
    }

    private class RecordingBody(
        private val oneShot: Boolean = false,
        private val duplex: Boolean = false,
        private val chunks: Int = 1,
    ) : RequestBody() {
        var writes = 0
        var isWrittenWhole = false
        override fun contentType(): MediaType? = null
        override fun isOneShot() = oneShot
        override fun isDuplex() = duplex
        override fun writeTo(sink: BufferedSink) {
            writes++
            repeat(chunks) { sink.writeUtf8("x".repeat(1_024)) }
            isWrittenWhole = true
        }
    }
}
