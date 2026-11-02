package io.netscope.export

import io.netscope.export.NetScopeExporter.MAX_TRANSFER_CHARS
import io.netscope.export.NetScopeExporter.forClipboard
import io.netscope.model.NetHeader
import io.netscope.model.NetTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetScopeExporterTest {

    private fun aTransaction(
        query: String? = null,
        headers: List<NetHeader> = emptyList(),
        body: String? = null,
    ) = NetTransaction(
        requestDate = 0L,
        method = "POST",
        scheme = "https",
        host = "api.example.com",
        path = "/v1",
        query = query,
        requestHeaders = headers,
        requestBody = body,
        requestContentType = null,
        requestBodySize = 0L,
    )

    @Test
    fun `should escape a single quote in a header value when generating curl`() {
        // Given
        val transaction = aTransaction(headers = listOf(NetHeader(name = "User-Agent", value = "it's")))

        // When
        val result = NetScopeExporter.toCurl(transaction)

        // Then
        assertTrue(result.contains("""-H 'User-Agent: it'\''s'"""))
    }

    @Test
    fun `should keep a header value quoted when it tries to run a command`() {
        // Given
        val transaction = aTransaction(headers = listOf(NetHeader(name = "X", value = "'; rm -rf /; '")))

        // When
        val result = NetScopeExporter.toCurl(transaction)

        // Then
        assertTrue(result.contains("""-H 'X: '\''; rm -rf /; '\'''"""))
    }

    @Test
    fun `should escape a single quote in the url when generating curl`() {
        // When
        val result = NetScopeExporter.toCurl(aTransaction(query = "q=it's"))

        // Then
        assertEquals("""curl -X POST 'https://api.example.com/v1?q=it'\''s'""", result)
    }

    @Test
    fun `should escape a single quote in the body when generating curl`() {
        // When
        val result = NetScopeExporter.toCurl(aTransaction(body = "it's"))

        // Then
        assertTrue(result.endsWith("""--data-raw 'it'\''s'"""))
    }

    @Test
    fun `should write the library version in a har`() {
        // When
        val result = NetScopeExporter.toHar(emptyList())

        // Then
        assertTrue(result.contains(""""version": "${BuildConfig.VERSION_NAME}""""))
    }

    @Test
    fun `should leave text at the limit whole for the clipboard`() {
        // Given
        val text = "x".repeat(MAX_TRANSFER_CHARS)

        // When
        val result = text.forClipboard()

        // Then
        assertEquals(text, result)
    }

    @Test
    fun `should cut text over the limit and say how much is missing`() {
        // Given
        val text = "x".repeat(MAX_TRANSFER_CHARS + 5)

        // When
        val result = text.forClipboard()

        // Then
        assertEquals("x".repeat(MAX_TRANSFER_CHARS) + "\n… [cut by NetScope: 5 more characters]", result)
    }
}
