package io.netscope.domain.usecase.transaction

import app.cash.turbine.test
import io.netscope.fake.FakeTransactionRepository
import io.netscope.fake.aTransaction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObserveTransactionUseCaseTest {

    @Test
    fun `should emit each version then null when the transaction changes and is deleted`() = runTest {
        // Given
        val pending = aTransaction(id = 1L, responseCode = null)
        val completed = pending.copy(responseCode = 200)
        val repository = FakeTransactionRepository(transactions = listOf(pending))

        ObserveTransactionUseCase(repository = repository)(id = 1L).test {
            assertEquals(pending, awaitItem())

            // When
            repository.update(transaction = completed)
            val updated = awaitItem()
            repository.clearAll()

            // Then
            assertEquals(completed, updated)
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
