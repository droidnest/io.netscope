package io.netscope

import org.junit.Assert.assertEquals
import org.junit.Test

class NetScopeConfigTest {

    @Test
    fun `should lower-case the redacted headers when normalizing`() {
        // Given
        val config = NetScopeConfig(redactedHeaders = setOf("X-Api-Key", "Cookie"))

        // When
        val result = config.withNormalizedHeaders()

        // Then
        assertEquals(setOf("x-api-key", "cookie"), result.redactedHeaders)
    }
}
