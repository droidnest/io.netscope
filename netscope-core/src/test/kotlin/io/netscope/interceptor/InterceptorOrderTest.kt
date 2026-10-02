package io.netscope.interceptor

import io.netscope.NetScopeConfig
import io.netscope.model.MockRule
import kotlinx.coroutines.Dispatchers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the documented chain order: `NetScope.interceptor()` first, `mockInterceptor()` second.
 * Uses the real [NetScopeMockInterceptor] with an injected rule source instead of the singleton.
 */
class InterceptorOrderTest {

    private val repo = FakeTransactionRepository()
    private val rule = MockRule(
        name = "items",
        urlPattern = "/api/items",
        statusCode = 418,
        responseBody = """{"mocked":true}""",
    )
    private val mock = NetScopeMockInterceptor { listOf(rule) }
    private val recorder = NetScopeInterceptor(repo, NetScopeConfig(), ioDispatcher = Dispatchers.Unconfined)
    private val network = Interceptor { error("mocked request must not reach the network") }

    private fun call(vararg interceptors: Interceptor): String {
        val client = OkHttpClient.Builder()
            .apply { interceptors.forEach { addInterceptor(it) } }
            .addInterceptor(network)
            .build()
        return client.newCall(Request.Builder().url("https://example.com/api/items").build())
            .execute().use { it.body.string() }
    }

    @Test
    fun `recorder before mock records the mocked response`() {
        val body = call(recorder, mock)

        assertEquals("""{"mocked":true}""", body)
        val recorded = repo.updated.single()
        assertEquals(418, recorded.responseCode)
        assertEquals("""{"mocked":true}""", recorded.responseBody)
    }

    @Test
    fun `mock before recorder short-circuits and nothing is recorded`() {
        call(mock, recorder)

        assertTrue(repo.inserted.isEmpty())
        assertTrue(repo.updated.isEmpty())
    }
}
