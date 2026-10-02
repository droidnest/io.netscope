package io.netscope

import io.netscope.model.MockRule
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetScopeNoOpTest {

    // ── mockInterceptor ───────────────────────────────────────────────────────

    @Test
    fun `mockInterceptor passes the request through even when a matching rule is added`() {
        NetScope.addMockRule(MockRule(name = "login", urlPattern = "/login", statusCode = 418))
        var seenByNext: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(NetScope.mockInterceptor())
            .addInterceptor(terminal { seenByNext = it })
            .build()
        val request = Request.Builder().url("https://example.com/login").build()

        val response = client.newCall(request).execute()

        assertEquals(request.url, seenByNext?.url)
        assertEquals(200, response.code)
        assertEquals("real", response.body?.string())
    }

    // ── API stubs ─────────────────────────────────────────────────────────────

    @Test
    fun `mock rules stay empty and config stays default`() {
        NetScope.addMockRule(MockRule(name = "x", urlPattern = "x"))
        NetScope.updateConfig { copy(maxTransactions = 1) }

        assertTrue(NetScope.mockRules.value.isEmpty())
        assertEquals(NetScopeConfig(), NetScope.config)
        assertFalse(NetScope.isInitialized())
        assertFalse(NetScope.shakeEnabled)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Short-circuits the chain with a canned 200 so no network is touched. */
    private fun terminal(onRequest: (Request) -> Unit) = Interceptor { chain ->
        onRequest(chain.request())
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("real".toResponseBody())
            .build()
    }
}
