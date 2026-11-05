package io.netscope.interceptor

import io.netscope.fake.aChain
import io.netscope.fake.aRequest
import io.netscope.model.MockRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetScopeMockInterceptorTest {

    private fun sut(contentType: String) = NetScopeMockInterceptor(
        rules = { listOf(MockRule(name = "Users", urlPattern = "/users", contentType = contentType)) },
    )

    @Test
    fun `should answer with the content type of the rule when it matches`() {
        // When
        val response = sut(contentType = "text/plain").intercept(aChain(request = aRequest()))

        // Then
        assertEquals("text/plain", response.header("Content-Type"))
    }

    @Test
    fun `should answer without the header when the content type cannot be sent`() {
        // When
        val response = sut(contentType = "application/jsön").intercept(aChain(request = aRequest()))

        // Then
        assertEquals(200, response.code)
        assertNull(response.header("Content-Type"))
    }
}
