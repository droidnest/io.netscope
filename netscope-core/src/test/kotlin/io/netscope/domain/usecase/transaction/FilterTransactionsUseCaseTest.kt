package io.netscope.domain.usecase.transaction

import io.netscope.fake.FakeTransactionRepository
import io.netscope.fake.aTransaction
import io.netscope.fake.toSummary
import io.netscope.model.NetTransaction
import io.netscope.model.SortOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterTransactionsUseCaseTest {

    private val getRow = aTransaction(id = 1L, method = "GET", path = "/users", durationMs = 10L, responseBodySize = 5L)
    private val postRow = aTransaction(id = 2L, method = "POST", path = "/orders", responseCode = 500, durationMs = 90L, responseBodySize = 1L)
    private val pendingRow = aTransaction(id = 3L, method = "PUT", path = "/carts", responseCode = null, durationMs = null, responseBodySize = 9L)

    private val sut = FilterTransactionsUseCase(
        repository = FakeTransactionRepository(transactions = listOf(getRow, postRow, pendingRow)),
    )

    private val get = getRow.toSummary()
    private val post = postRow.toSummary()
    private val pending = pendingRow.toSummary()

    @Test
    fun `should return everything in stored order when no filter is set`() = runTest {
        // When
        val result = sut().first()

        // Then
        assertEquals(listOf(get, post, pending), result)
    }

    @Test
    fun `should match the url ignoring case when searching`() = runTest {
        // When
        val result = sut(query = "ORDERS").first()

        // Then
        assertEquals(listOf(post), result)
    }

    @Test
    fun `should pass the query on to the repository when searching`() = runTest {
        // Given
        val withBody = aTransaction(id = 4L, path = "/x", responseBody = "needle inside")
        val filter = FilterTransactionsUseCase(
            repository = FakeTransactionRepository(transactions = listOf(getRow, withBody)),
        )

        // When
        val result = filter(query = "needle").first()

        // Then
        assertEquals(listOf(withBody.toSummary()), result)
    }

    @Test
    fun `should keep only the selected methods when filtering by method`() = runTest {
        // When
        val result = sut(methods = setOf("POST", "PUT")).first()

        // Then
        assertEquals(listOf(post, pending), result)
    }

    @Test
    fun `should keep only the selected status classes when filtering by status`() = runTest {
        // When
        val result = sut(statusClasses = setOf(NetTransaction.StatusClass.SERVER_ERROR)).first()

        // Then
        assertEquals(listOf(post), result)
    }

    @Test
    fun `should put the slowest first and pending last when sorting by duration`() = runTest {
        // When
        val result = sut(sortOrder = SortOrder.DURATION).first()

        // Then
        assertEquals(listOf(post, get, pending), result)
    }

    @Test
    fun `should put the largest first when sorting by size`() = runTest {
        // When
        val result = sut(sortOrder = SortOrder.SIZE).first()

        // Then
        assertEquals(listOf(pending, get, post), result)
    }
}
