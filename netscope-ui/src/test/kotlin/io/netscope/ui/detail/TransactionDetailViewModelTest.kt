package io.netscope.ui.detail

import app.cash.turbine.test
import io.netscope.domain.usecase.transaction.ObserveTransactionUseCase
import io.netscope.ui.MainDispatcherRule
import io.netscope.ui.fake.FakeTransactionRepository
import io.netscope.ui.fake.aTransaction
import io.netscope.ui.mvi.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pending = aTransaction(id = 1L, responseCode = null)
    private val other = aTransaction(id = 2L)
    private val repository = FakeTransactionRepository(transactions = listOf(pending, other))

    private lateinit var sut: TransactionDetailViewModel

    @Before
    fun setUp() {
        sut = TransactionDetailViewModel(
            observeTransactionUseCase = ObserveTransactionUseCase(repository = repository),
        )
    }

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sut.uiState.collect {} }
    }

    @Test
    fun `should stay idle when the screen has not started`() = runTest {
        // Given
        collectState()

        // Then
        assertEquals(UiState.Idle, sut.uiState.value)
    }

    @Test
    fun `should show the transaction of the route when started`() = runTest {
        // Given
        collectState()

        // When
        sut.onIntent(intent = TransactionDetailUiIntent.Started(transactionId = 1L))

        // Then
        assertEquals(UiState.Success(data = TransactionDetailUiState(transaction = pending)), sut.uiState.value)
    }

    @Test
    fun `should show the response when a pending transaction completes`() = runTest {
        // Given
        collectState()
        sut.onIntent(intent = TransactionDetailUiIntent.Started(transactionId = 1L))
        val completed = pending.copy(responseCode = 200)

        // When
        repository.transactions.value = listOf(completed, other)

        // Then
        assertEquals(completed, sut.uiState.value.dataOrNull()?.transaction)
    }

    @Test
    fun `should emit idle then the content then the error when the transaction is deleted`() = runTest {
        sut.uiState.test {
            // Given
            assertEquals(UiState.Idle, awaitItem())

            // When
            sut.onIntent(intent = TransactionDetailUiIntent.Started(transactionId = 1L))
            val content = awaitItem()
            repository.transactions.value = listOf(other)

            // Then
            assertEquals(UiState.Success(data = TransactionDetailUiState(transaction = pending)), content)
            assertTrue(awaitItem() is UiState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should keep the first transaction when started again`() = runTest {
        // Given
        collectState()
        sut.onIntent(intent = TransactionDetailUiIntent.Started(transactionId = 1L))

        // When
        sut.onIntent(intent = TransactionDetailUiIntent.Started(transactionId = 2L))

        // Then
        assertEquals(pending, sut.uiState.value.dataOrNull()?.transaction)
    }

    @Test
    fun `should show an error when the transaction is deleted`() = runTest {
        // Given
        collectState()
        sut.onIntent(intent = TransactionDetailUiIntent.Started(transactionId = 1L))

        // When
        repository.transactions.value = listOf(other)

        // Then
        assertTrue(sut.uiState.value is UiState.Error)
    }

    @Test
    fun `should go back to idle when the error is shown`() = runTest {
        // Given
        collectState()
        sut.onIntent(intent = TransactionDetailUiIntent.Started(transactionId = 1L))
        repository.transactions.value = listOf(other)

        // When
        sut.onIntent(intent = TransactionDetailUiIntent.ErrorShown)

        // Then
        assertEquals(UiState.Idle, sut.uiState.value)
    }

    @Test
    fun `should keep the selected tab when the transaction changes`() = runTest {
        // Given
        collectState()
        sut.onIntent(intent = TransactionDetailUiIntent.Started(transactionId = 1L))
        sut.onIntent(intent = TransactionDetailUiIntent.TabClicked(tab = 2))

        // When
        repository.transactions.value = listOf(pending.copy(responseCode = 200), other)

        // Then
        assertEquals(2, sut.uiState.value.dataOrNull()?.selectedTab)
    }

    @Test
    fun `should open then close the menu when it is clicked and dismissed`() = runTest {
        // Given
        collectState()
        sut.onIntent(intent = TransactionDetailUiIntent.Started(transactionId = 1L))

        // When
        sut.onIntent(intent = TransactionDetailUiIntent.MenuClicked)
        val opened = sut.uiState.value.dataOrNull()?.isMenuOpen
        sut.onIntent(intent = TransactionDetailUiIntent.MenuDismissed)

        // Then
        assertEquals(true, opened)
        assertFalse(sut.uiState.value.dataOrNull()!!.isMenuOpen)
    }
}
