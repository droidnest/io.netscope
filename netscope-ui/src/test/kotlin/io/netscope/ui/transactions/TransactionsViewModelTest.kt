package io.netscope.ui.transactions

import io.netscope.domain.usecase.stats.ComputeTransactionStatsUseCase
import io.netscope.domain.usecase.transaction.ClearTransactionsUseCase
import io.netscope.domain.usecase.transaction.FilterTransactionsUseCase
import io.netscope.model.NetTransaction
import io.netscope.model.SortOrder
import io.netscope.ui.MainDispatcherRule
import io.netscope.ui.fake.FakeTransactionRepository
import io.netscope.ui.fake.aTransaction
import io.netscope.ui.mvi.UiIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val get = aTransaction(id = 1L, method = "GET", path = "/users")
    private val post = aTransaction(id = 2L, method = "POST", path = "/orders", responseCode = 500)
    private val repository = FakeTransactionRepository(transactions = listOf(get, post))

    private lateinit var sut: TransactionsViewModel

    @Before
    fun setUp() {
        sut = TransactionsViewModel(
            filterTransactionsUseCase = FilterTransactionsUseCase(repository = repository),
            computeTransactionStatsUseCase = ComputeTransactionStatsUseCase(),
            clearTransactionsUseCase = ClearTransactionsUseCase(repository = repository),
            defaultDispatcher = mainDispatcherRule.dispatcher,
        )
    }

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sut.uiState.collect {} }
    }

    private val state: TransactionsUiState
        get() = sut.uiState.value.dataOrNull()!!

    @Test
    fun `should show every transaction and its figures when nothing is filtered`() = runTest {
        // Given
        collectState()

        // Then
        assertEquals(listOf(get, post), state.content.transactions)
        assertEquals(2, state.content.stats.totalCount)
        assertFalse(state.content.isFiltering)
    }

    @Test
    fun `should narrow the list and the figures when a query is typed`() = runTest {
        // Given
        collectState()

        // When
        sut.onIntent(intent = TransactionsUiIntent.QueryTyped(query = "orders"))

        // Then
        assertEquals(listOf(post), state.content.transactions)
        assertEquals(1, state.content.stats.totalCount)
        assertEquals("orders", state.content.query)
        assertTrue(state.content.isFiltering)
    }

    @Test
    fun `should show new rows when the database changes`() = runTest {
        // Given
        collectState()
        val added = aTransaction(id = 3L)

        // When
        repository.transactions.value = listOf(get, post, added)

        // Then
        assertEquals(listOf(get, post, added), state.content.transactions)
    }

    @Test
    fun `should add then remove the method when it is toggled twice`() = runTest {
        // Given
        collectState()

        // When
        sut.onIntent(intent = TransactionsUiIntent.MethodToggled(method = "POST"))
        val filtered = state.content.transactions
        sut.onIntent(intent = TransactionsUiIntent.MethodToggled(method = "POST"))

        // Then
        assertEquals(listOf(post), filtered)
        assertEquals(listOf(get, post), state.content.transactions)
    }

    @Test
    fun `should filter by status class when one is toggled`() = runTest {
        // Given
        collectState()

        // When
        sut.onIntent(intent = TransactionsUiIntent.StatusClassToggled(statusClass = NetTransaction.StatusClass.SUCCESS))

        // Then
        assertEquals(listOf(get), state.content.transactions)
    }

    @Test
    fun `should close the sort menu when a sort order is selected`() = runTest {
        // Given
        collectState()
        sut.onIntent(intent = TransactionsUiIntent.MenuClicked(menu = TransactionsMenu.SORT))

        // When
        sut.onIntent(intent = TransactionsUiIntent.SortOrderSelected(sortOrder = SortOrder.SIZE))

        // Then
        assertEquals(SortOrder.SIZE, state.content.filter.sortOrder)
        assertNull(state.content.openMenu)
    }

    @Test
    fun `should open then close a menu when it is clicked and dismissed`() = runTest {
        // Given
        collectState()

        // When
        sut.onIntent(intent = TransactionsUiIntent.MenuClicked(menu = TransactionsMenu.EXPORT))
        val opened = state.content.openMenu
        sut.onIntent(intent = TransactionsUiIntent.MenuDismissed)

        // Then
        assertEquals(TransactionsMenu.EXPORT, opened)
        assertNull(state.content.openMenu)
    }

    @Test
    fun `should reset the query and the filter when the filters are cleared`() = runTest {
        // Given
        collectState()
        sut.onIntent(intent = TransactionsUiIntent.QueryTyped(query = "orders"))
        sut.onIntent(intent = TransactionsUiIntent.MethodToggled(method = "POST"))

        // When
        sut.onIntent(intent = TransactionsUiIntent.ClearFiltersClicked)

        // Then
        assertFalse(state.content.isFiltering)
        assertEquals(listOf(get, post), state.content.transactions)
    }

    @Test
    fun `should empty the list when clear all is clicked`() = runTest {
        // Given
        collectState()

        // When
        sut.onIntent(intent = TransactionsUiIntent.ClearAllClicked)

        // Then
        assertEquals(emptyList<NetTransaction>(), state.content.transactions)
    }

    @Test
    fun `should navigate then go back to content when a transaction is opened`() = runTest {
        // Given
        collectState()

        // When
        sut.onIntent(intent = TransactionsUiIntent.TransactionClicked(id = 2L))
        val navigating = state
        sut.onIntent(intent = TransactionsUiIntent.NavigationHandled)

        // Then
        assertEquals(2L, (navigating as TransactionsUiState.Navigating).openedTransactionId)
        assertTrue(state is TransactionsUiState.Content)
    }

    @Test
    fun `should ignore an intent of another flow`() = runTest {
        // Given
        collectState()
        val before = sut.uiState.value

        // When
        sut.onIntent(intent = object : UiIntent {})

        // Then
        assertEquals(before, sut.uiState.value)
    }
}
