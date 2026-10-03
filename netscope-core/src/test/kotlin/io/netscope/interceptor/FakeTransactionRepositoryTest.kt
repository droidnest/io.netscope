package io.netscope.interceptor

import io.netscope.model.NetTransaction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeTransactionRepositoryTest {

    private val repo = FakeTransactionRepository()

    private fun pending() = NetTransaction(
        requestDate = 1_000L,
        method = "GET",
        scheme = "https",
        host = "example.com",
        path = "/",
        query = null,
        requestHeaders = emptyList(),
        requestBody = "req",
        requestContentType = null,
        requestBodySize = 3L,
    )

    @Test
    fun `update writes response columns only and preserves timing and request fields`() = runBlocking {
        val id = repo.insert(pending())
        repo.updateTiming(id, dnsMs = 1, connectMs = 2, sslMs = 3, ttfbMs = 4)

        // A full row as the interceptor builds it: no timing, different request fields.
        repo.update(pending().copy(id = id, requestBody = null, responseCode = 200, durationMs = 50, responseDate = 1_050L))

        val row = repo.row(id)!!
        assertEquals(listOf(1L, 2L, 3L, 4L), listOf(row.dnsMs, row.connectMs, row.sslMs, row.ttfbMs))
        assertEquals("req", row.requestBody)
        assertEquals(200, row.responseCode)
        assertEquals(50L, row.durationMs)
    }
}
