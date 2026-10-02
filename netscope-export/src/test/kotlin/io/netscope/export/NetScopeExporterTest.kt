package io.netscope.export

import com.google.gson.JsonParser
import io.netscope.model.NetHeader
import io.netscope.model.NetTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class NetScopeExporterTest {

    // ── cURL ──────────────────────────────────────────────────────────────────

    @Test
    fun `toCurl single-quotes url, headers and body`() {
        val tx = transaction(
            path = "/it's",
            query = "q='x'",
            headers = listOf(NetHeader("X-Note", "a'b")),
            body = """{"name":"O'Brien"}""",
        )

        val curl = NetScopeExporter.toCurl(tx)

        assertEquals(
            """
            curl -X 'POST' 'https://example.com/it'\''s?q='\''x'\''' \
              -H 'X-Note: a'\''b' \
              --data-raw '{"name":"O'\''Brien"}'
            """.trimIndent(),
            curl,
        )
    }

    @Test
    fun `toCurl output cannot inject shell commands`() {
        assumeTrue(File("/bin/sh").canExecute())
        val marker = File.createTempFile("netscope", ".pwned").apply { delete() }
        val payload = "'; touch ${marker.absolutePath}; echo '"
        val tx = transaction(
            path = "/a$payload",
            headers = listOf(NetHeader("X$payload", "v$payload")),
            body = "b$payload",
        )

        // Replace curl with a function that prints each argument on its own line.
        val script = "curl() { for a in \"\$@\"; do printf '%s\\n' \"\$a\"; done; }\n" +
            NetScopeExporter.toCurl(tx)
        val process = ProcessBuilder("/bin/sh", "-c", script).redirectErrorStream(true).start()
        val args = process.inputStream.bufferedReader().readLines()
        process.waitFor()

        assertTrue("payload executed", !marker.exists())
        assertEquals(
            listOf(
                "-X", "POST",
                "https://example.com/a$payload",
                "-H", "X$payload: v$payload",
                "--data-raw", "b$payload",
            ),
            args,
        )
    }

    // ── HAR ───────────────────────────────────────────────────────────────────

    @Test
    fun `toHar reports the library version as creator version`() {
        val har = JsonParser.parseString(NetScopeExporter.toHar(listOf(transaction())))
            .asJsonObject.getAsJsonObject("log")

        val creator = har.getAsJsonObject("creator")
        assertEquals("NetScope", creator["name"].asString)
        assertEquals(BuildConfig.VERSION_NAME, creator["version"].asString)
        assertTrue(BuildConfig.VERSION_NAME.matches(Regex("""\d+\.\d+\.\d+.*""")))
        assertEquals(1, har.getAsJsonArray("entries").size())
    }

    // ── parseQueryString ──────────────────────────────────────────────────────

    @Test
    fun `parseQueryString splits pairs and keeps empty values`() {
        assertEquals(
            listOf(
                mapOf("name" to "a", "value" to "1"),
                mapOf("name" to "b", "value" to ""),
                mapOf("name" to "c", "value" to "x=y"),
            ),
            NetScopeExporter.parseQueryString("a=1&b=&c=x=y"),
        )
    }

    @Test
    fun `parseQueryString drops pairs without equals and handles blank input`() {
        assertEquals(
            listOf(mapOf("name" to "k", "value" to "v")),
            NetScopeExporter.parseQueryString("flag&k=v"),
        )
        assertEquals(emptyList<Map<String, String>>(), NetScopeExporter.parseQueryString(null))
        assertEquals(emptyList<Map<String, String>>(), NetScopeExporter.parseQueryString(" "))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun transaction(
        path: String = "/v1/login",
        query: String? = null,
        headers: List<NetHeader> = emptyList(),
        body: String? = null,
    ) = NetTransaction(
        requestDate = 0L,
        method = "POST",
        scheme = "https",
        host = "example.com",
        path = path,
        query = query,
        requestHeaders = headers,
        requestBody = body,
        requestContentType = "application/json",
        requestBodySize = body?.length?.toLong() ?: 0L,
    )
}
