package io.netscope.data.repository

import io.netscope.model.SettingChange
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryImplTest {

    @Test
    fun `should keep the configured headers when a change removes one`() {
        // Given
        val change = SettingChange.RedactedHeaders(headers = setOf("x-api-key"))

        // When
        val result = change.keepingRedactionsOf(configuredHeaders = setOf("authorization"))

        // Then
        assertEquals(SettingChange.RedactedHeaders(headers = setOf("authorization", "x-api-key")), result)
    }

    @Test
    fun `should lower-case the headers of a change`() {
        // Given
        val change = SettingChange.RedactedHeaders(headers = setOf("X-Api-Key"))

        // When
        val result = change.keepingRedactionsOf(configuredHeaders = emptySet())

        // Then
        assertEquals(SettingChange.RedactedHeaders(headers = setOf("x-api-key")), result)
    }

    @Test
    fun `should leave any other change as it is`() {
        // Given
        val change = SettingChange.MaxTransactions(count = 10)

        // When
        val result = change.keepingRedactionsOf(configuredHeaders = setOf("authorization"))

        // Then
        assertEquals(change, result)
    }
}
