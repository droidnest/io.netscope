package io.netscope.ui.viewmodel

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.netscope.domain.repository.TransactionRepository
import io.netscope.domain.usecase.ClearTransactionsUseCase
import io.netscope.domain.usecase.ExportTransactionsUseCase
import io.netscope.domain.usecase.FilterTransactionsUseCase
import io.netscope.domain.usecase.ObserveTransactionUseCase
import io.netscope.model.NetTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetScopeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(repository: TransactionRepository) = NetScopeViewModel(
        filterTransactionsUseCase = FilterTransactionsUseCase(repository, testDispatcher),
        observeTransactionUseCase = ObserveTransactionUseCase(repository),
        exportTransactionsUseCase = ExportTransactionsUseCase(repository, testDispatcher),
        clearTransactionsUseCase  = ClearTransactionsUseCase(repository),
        defaultDispatcher         = testDispatcher,
    )

    // ── Detail ────────────────────────────────────────────────────────────────

    @Test
    fun `selecting an id emits the live transaction and re-emits when its response lands`() = runTest {
        // Given a pending transaction
        val pending = transaction(id = 1, responseCode = null, durationMs = null, responseBody = null)
        val repository = FakeTransactionRepository(listOf(pending))
        val viewModel = createViewModel(repository)

        viewModel.selectedTransaction.test {
            assertNull(awaitItem())

            // When it is selected
            viewModel.selectTransaction(1)

            // Then the pending row is emitted
            assertEquals(NetTransaction.StatusClass.PENDING, awaitItem()?.statusClass)

            // When the response lands
            repository.update(pending.copy(responseCode = 200, durationMs = 42, responseBody = "done"))

            // Then the completed row is re-emitted, bodies included
            val completed = awaitItem()
            assertEquals(200, completed?.responseCode)
            assertEquals("done", completed?.responseBody)
            assertEquals("request-body-1", completed?.requestBody)
        }
    }

    @Test
    fun `clearSelection emits null`() = runTest {
        // Given a selected transaction
        val viewModel = createViewModel(FakeTransactionRepository(listOf(transaction(id = 1))))

        viewModel.selectedTransaction.test {
            assertNull(awaitItem())
            viewModel.selectTransaction(1)
            assertEquals(1L, awaitItem()?.id)

            // When the selection is cleared
            viewModel.clearSelection()

            // Then null is emitted
            assertNull(awaitItem())
        }
    }

    // ── Filters ───────────────────────────────────────────────────────────────

    @Test
    fun `method filter toggle is applied and toggling again removes it`() = runTest {
        // Given GET and POST transactions
        val viewModel = createViewModel(
            FakeTransactionRepository(listOf(transaction(id = 1, method = "GET"), transaction(id = 2, method = "POST"))),
        )

        viewModel.transactions.test {
            assertEquals(2, awaitItem().size)

            // When filtering on POST
            viewModel.toggleMethodFilter("POST")

            // Then only POST remains
            assertEquals(listOf(2L), awaitItem().map { it.id })
            assertEquals(setOf("POST"), viewModel.filterState.value.methods)

            // When toggling POST off again
            viewModel.toggleMethodFilter("POST")

            // Then every transaction is back
            assertEquals(2, awaitItem().size)
            assertFalse(viewModel.filterState.value.isActive)
        }
    }

    @Test
    fun `status class filter toggle is applied`() = runTest {
        // Given a success and a server error
        val viewModel = createViewModel(
            FakeTransactionRepository(listOf(transaction(id = 1, responseCode = 200), transaction(id = 2, responseCode = 503))),
        )

        viewModel.transactions.test {
            assertEquals(2, awaitItem().size)

            // When filtering on 5xx
            viewModel.toggleStatusClassFilter(NetTransaction.StatusClass.SERVER_ERROR)

            // Then only the server error remains
            assertEquals(listOf(2L), awaitItem().map { it.id })
        }
    }

    @Test
    fun `list rows carry no bodies`() = runTest {
        // Given a transaction with bodies
        val viewModel = createViewModel(FakeTransactionRepository(listOf(transaction(id = 1))))

        // When the list is observed
        viewModel.transactions.test {
            val row = awaitItem().ifEmpty { awaitItem() }.single() // skip the initial empty state if seen

            // Then the list projection has no bodies
            assertNull(row.requestBody)
            assertNull(row.responseBody)
        }
    }

    // ── Export ────────────────────────────────────────────────────────────────

    @Test
    fun `HAR export contains full bodies of the visible transactions only`() = runTest {
        // Given a GET and a POST, with the list filtered to POST
        val viewModel = createViewModel(
            FakeTransactionRepository(listOf(transaction(id = 1, method = "GET"), transaction(id = 2, method = "POST"))),
        )
        viewModel.toggleMethodFilter("POST")

        viewModel.harExports.test {
            // When exporting
            viewModel.exportVisibleAsHar()

            // Then the HAR is built from the full-body POST row only
            val har = awaitItem()
            assertTrue(har.contains("request-body-2"))
            assertTrue(har.contains("response-body-2"))
            assertFalse(har.contains("request-body-1"))
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @Test
    fun `stats are computed from the filtered list`() = runTest {
        // Given three transactions, one of them a client error
        val repository = FakeTransactionRepository(
            listOf(
                transaction(id = 1, durationMs = 50),
                transaction(id = 2, durationMs = 150),
                transaction(id = 3, responseCode = 404, durationMs = 400),
            ),
        )
        val viewModel = createViewModel(repository)

        viewModel.stats.test {
            // When the list is loaded
            val stats = expectMostRecentItemMatching { it != null && it.totalCount > 0 }

            // Then the aggregates reflect it
            assertEquals(3, stats.totalCount)
            assertEquals(1, stats.errorCount)
            assertEquals(200L, stats.avgDurationMs)
            assertEquals(listOf("GET" to 3), stats.methodCounts)

            // When the list is cleared
            repository.clearAll()

            // Then the stats are recomputed as empty
            assertEquals(0, awaitItem()?.totalCount)
        }
    }

    private suspend fun ReceiveTurbine<StatsData?>.expectMostRecentItemMatching(
        predicate: (StatsData?) -> Boolean,
    ): StatsData {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item!!
        }
    }
}

// ── Test doubles ──────────────────────────────────────────────────────────────

/** In-memory [TransactionRepository] honouring the real contract (list projection has no bodies). */
private class FakeTransactionRepository(initial: List<NetTransaction> = emptyList()) : TransactionRepository {

    private val rows = MutableStateFlow(initial.sortedByDescending { it.requestDate })
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override suspend fun insert(transaction: NetTransaction): Long {
        val id = nextId++
        rows.update { (it + transaction.copy(id = id)).sortedByDescending { tx -> tx.requestDate } }
        return id
    }

    override suspend fun update(transaction: NetTransaction) {
        rows.update { list -> list.map { if (it.id == transaction.id) transaction else it } }
    }

    override fun observeAll(query: String): Flow<List<NetTransaction>> = rows.map { list ->
        list
            .filter { query.isBlank() || it.url.contains(query, ignoreCase = true) }
            .map { it.copy(requestBody = null, responseBody = null) }
    }

    override fun observeById(id: Long): Flow<NetTransaction?> =
        rows.map { list -> list.firstOrNull { it.id == id } }.distinctUntilChanged()

    override suspend fun getById(id: Long): NetTransaction? = rows.value.firstOrNull { it.id == id }

    override suspend fun getAllWithBodies(): List<NetTransaction> = rows.value

    override suspend fun updateTiming(id: Long, dnsMs: Long?, connectMs: Long?, sslMs: Long?, ttfbMs: Long?) {
        rows.update { list ->
            list.map { if (it.id == id) it.copy(dnsMs = dnsMs, connectMs = connectMs, sslMs = sslMs, ttfbMs = ttfbMs) else it }
        }
    }

    override suspend fun clearAll() {
        rows.value = emptyList()
    }
}

private fun transaction(
    id: Long,
    method: String = "GET",
    path: String = "/items/$id",
    responseCode: Int? = 200,
    durationMs: Long? = 100L,
    requestBody: String? = "request-body-$id",
    responseBody: String? = "response-body-$id",
) = NetTransaction(
    id                 = id,
    requestDate        = 1_000L * id,
    durationMs         = durationMs,
    method             = method,
    scheme             = "https",
    host               = "api.example.com",
    path               = path,
    query              = null,
    requestHeaders     = emptyList(),
    requestBody        = requestBody,
    requestContentType = "text/plain",
    requestBodySize    = requestBody?.length?.toLong() ?: 0L,
    responseCode       = responseCode,
    responseBody       = responseBody,
    responseBodySize   = responseBody?.length?.toLong() ?: 0L,
)
