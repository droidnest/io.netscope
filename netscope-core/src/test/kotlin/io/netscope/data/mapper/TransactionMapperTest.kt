package io.netscope.data.mapper

import io.netscope.data.mapper.TransactionMapper.MAX_STORED_BODY_BYTES
import io.netscope.data.mapper.TransactionMapper.toEntity
import io.netscope.data.mapper.TransactionMapper.toEntityWithoutTiming
import io.netscope.model.NetTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionMapperTest {

    private fun aTransaction(body: String) = NetTransaction(
        requestDate = 0L,
        method = "POST",
        scheme = "https",
        host = "api.example.com",
        path = "/",
        query = null,
        requestHeaders = emptyList(),
        requestBody = body,
        requestContentType = null,
        requestBodySize = 0L,
        responseBody = body,
    )

    private fun String.utf8Size() = toByteArray().size

    @Test
    fun `should keep a body that fits when mapping`() {
        // Given
        val transaction = aTransaction(body = "é".repeat(1_000))

        // When
        val entity = transaction.toEntity()

        // Then
        assertEquals(transaction.requestBody, entity.requestBody)
    }

    @Test
    fun `should cut a body of replacement characters to the stored limit when mapping`() {
        // Given
        val transaction = aTransaction(body = "�".repeat(1_000_000))

        // When
        val entity = transaction.toEntity()

        // Then
        assertTrue(entity.requestBody!!.utf8Size() <= MAX_STORED_BODY_BYTES)
        assertTrue(entity.responseBody!!.utf8Size() > MAX_STORED_BODY_BYTES - 3)
    }

    @Test
    fun `should cut both bodies when mapping a response update`() {
        // Given
        val transaction = aTransaction(body = "x".repeat(MAX_STORED_BODY_BYTES + 1))

        // When
        val entity = transaction.toEntityWithoutTiming()

        // Then
        assertEquals(MAX_STORED_BODY_BYTES, entity.requestBody!!.length)
        assertEquals(MAX_STORED_BODY_BYTES, entity.responseBody!!.length)
    }

    @Test
    fun `should not split a surrogate pair when cutting`() {
        // Given
        val transaction = aTransaction(body = "😀".repeat(MAX_STORED_BODY_BYTES))

        // When
        val entity = transaction.toEntity()

        // Then
        assertTrue(entity.requestBody!!.last().isLowSurrogate())
    }
}
