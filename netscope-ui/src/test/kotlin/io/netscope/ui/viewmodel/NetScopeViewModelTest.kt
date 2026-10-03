package io.netscope.ui.viewmodel

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.netscope.domain.repository.TransactionRepository
import io.netscope.domain.usecase.ClearTransactionsUseCase
import io.netscope.domain.usecase.ExportTransactionsUseCase
import io.netscope.domain.usecase.FilterTransactionsUseCase
import io.netscope.domain.usecase.ObserveTransactionUseCase
import io.netscope.domain.usecase.SortOrder
import io.netscope.model.NetTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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
    fun `detailState goes from Loading to Loaded and re-emits when the response lands`() = runTest {
        // Given a pending transaction
        val pending = transaction(id = 1, responseCode = null, durationMs = null, responseBody = null)
        val repository = FakeTransactionRepository(listOf(pending))
        val viewModel = createViewModel(repository)

        viewModel.detailState.test {
            assertEquals(DetailUiState.Loading, awaitItem())

            // When it is selected
            viewModel.selectTransaction(1)

            // Then the pending row is loaded
            assertEquals(NetTransaction.StatusClass.PENDING, awaitLoaded().statusClass)

            // When the response lands
            repository.update(pending.copy(responseCode = 200, durationMs = 42, responseBody = "done"))

            // Then the completed row is re-emitted, bodies included
            val completed = awaitLoaded()
            assertEquals(200, completed.responseCode)
            assertEquals("done", completed.responseBody)
            assertEquals("request-body-1", completed.requestBody)
        }
    }

    @Test
    fun `detailState is Missing when the selected row is deleted`() = runTest {
        // Given a loaded transaction
        val repository = FakeTransactionRepository(listOf(transaction(id = 1)))
        val viewModel = createViewModel(repository)

        viewModel.detailState.test {
            assertEquals(DetailUiState.Loading, awaitItem())
            viewModel.selectTransaction(1)
            assertEquals(1L, awaitLoaded().id)

            // When the row is pruned
            repository.remove(1)

            // Then the state is Missing, not Loading
            assertEquals(DetailUiState.Missing, awaitItem())
        }
    }

    @Test
    fun `detailState for an unknown id is Loading until the DB answers, then Missing`() = runTest {
        // Given a DB that answers after a delay
        val viewModel = createViewModel(FakeTransactionRepository(listOf(transaction(id = 1)), observeDelayMs = 50))
        backgroundScope.launch(testDispatcher) { viewModel.detailState.collect {} }

        // When an unknown id is selected
        viewModel.selectTransaction(99)

        // Then it is Loading, then Missing
        assertEquals(DetailUiState.Loading, viewModel.detailState.value)
        advanceUntilIdle()
        assertEquals(DetailUiState.Missing, viewModel.detailState.value)
    }

    @Test
    fun `switching id resets to Loading synchronously and never shows the previous row`() = runTest {
        // Given transaction 1 loaded, with a DB that answers after a delay
        val repository = FakeTransactionRepository(listOf(transaction(id = 1), transaction(id = 2)), observeDelayMs = 50)
        val viewModel = createViewModel(repository)
        val seen = mutableListOf<DetailUiState>()
        backgroundScope.launch(testDispatcher) { viewModel.detailState.collect { seen += it } }
        viewModel.selectTransaction(1)
        advanceUntilIdle()
        assertEquals(1L, (viewModel.detailState.value as DetailUiState.Loaded).transaction.id)
        seen.clear()

        // When another id is selected
        viewModel.selectTransaction(2)

        // Then the state is Loading immediately, before the DB answers
        assertEquals(DetailUiState.Loading, viewModel.detailState.value)

        // And then the new row, never the old one
        advanceUntilIdle()
        assertEquals(2L, (viewModel.detailState.value as DetailUiState.Loaded).transaction.id)
        assertTrue(seen.none { it is DetailUiState.Loaded && it.transaction.id == 1L })
    }

    @Test
    fun `resubscribing for the same id keeps the loaded row and resumes live updates`() = runTest {
        // Given a loaded transaction
        val tx = transaction(id = 1, responseCode = null, durationMs = null)
        val repository = FakeTransactionRepository(listOf(tx))
        val viewModel = createViewModel(repository)
        viewModel.detailState.test {
            skipItems(1)
            viewModel.selectTransaction(1)
            awaitLoaded()
        }

        // When the screen is away longer than the stop timeout, then comes back
        advanceTimeBy(10_000)
        // (process-death restore path re-selects the same id: must be a no-op)
        viewModel.selectTransaction(1)

        viewModel.detailState.test {
            // Then the last row is shown straight away — no regression to Loading
            assertEquals(1L, awaitLoaded().id)

            // And live updates resume
            repository.update(tx.copy(responseCode = 200, durationMs = 7))
            assertEquals(200, awaitLoaded().responseCode)
        }
    }

    @Test
    fun `detail observation stops after the stop timeout when nobody collects`() = runTest {
        // Given a selected, observed transaction
        val repository = FakeTransactionRepository(listOf(transaction(id = 1)))
        val viewModel = createViewModel(repository)
        viewModel.detailState.test {
            skipItems(1)
            viewModel.selectTransaction(1)
            awaitLoaded()
        }

        // When nobody collects for longer than the timeout
        advanceTimeBy(5_001)

        // Then the row is no longer observed
        assertEquals(0, repository.activeByIdObservers)
    }

    @Test
    fun `clearSelection resets to Loading`() = runTest {
        // Given a selected transaction
        val viewModel = createViewModel(FakeTransactionRepository(listOf(transaction(id = 1))))

        viewModel.detailState.test {
            skipItems(1)
            viewModel.selectTransaction(1)
            awaitLoaded()

            // When the selection is cleared
            viewModel.clearSelection()

            // Then the state is Loading
            assertEquals(DetailUiState.Loading, awaitItem())
        }
    }

    private suspend fun ReceiveTurbine<DetailUiState>.awaitLoaded(): NetTransaction {
        val item = awaitItem()
        assertTrue("expected Loaded but was $item", item is DetailUiState.Loaded)
        return (item as DetailUiState.Loaded).transaction
    }

    // ── Search debounce ───────────────────────────────────────────────────────

    @Test
    fun `rapid typing runs one database search per settle while the text field updates instantly`() = runTest {
        // Given an observed list
        val repository = FakeTransactionRepository(listOf(transaction(id = 1)))
        val viewModel = createViewModel(repository)
        backgroundScope.launch(testDispatcher) { viewModel.transactions.collect {} }
        assertEquals(listOf(""), repository.summaryQueries)

        // When typing quickly
        viewModel.onSearchQueryChange("i")
        advanceTimeBy(100)
        viewModel.onSearchQueryChange("it")
        advanceTimeBy(100)
        viewModel.onSearchQueryChange("ite")

        // Then the text field sees every keystroke, but nothing is queried yet
        assertEquals("ite", viewModel.searchQuery.value)
        advanceTimeBy(NetScopeViewModel.SEARCH_DEBOUNCE_MS - 1)
        assertEquals(listOf(""), repository.summaryQueries)

        // When the typing settles
        advanceTimeBy(2)

        // Then exactly one query runs, for the final text
        assertEquals(listOf("", "ite"), repository.summaryQueries)

        // When only whitespace changes, no new scan runs
        viewModel.onSearchQueryChange("ite ")
        advanceTimeBy(1_000)
        assertEquals(listOf("", "ite"), repository.summaryQueries)
    }

    @Test
    fun `filter chips apply immediately and clearing the search is not debounced`() = runTest {
        // Given an observed list with a settled search
        val repository = FakeTransactionRepository(listOf(transaction(id = 1, method = "GET"), transaction(id = 2, method = "POST")))
        val viewModel = createViewModel(repository)
        backgroundScope.launch(testDispatcher) { viewModel.transactions.collect {} }
        viewModel.onSearchQueryChange("items")
        advanceTimeBy(NetScopeViewModel.SEARCH_DEBOUNCE_MS + 1)
        assertEquals(2, viewModel.transactions.value.size)

        // When a chip is toggled
        viewModel.toggleMethodFilter("POST")

        // Then the list updates without waiting
        assertEquals(listOf(2L), viewModel.transactions.value.map { it.id })

        // When the search is cleared
        viewModel.onSearchQueryChange("")

        // Then the unfiltered query runs at once
        assertEquals("", repository.summaryQueries.last())
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
    fun `HAR export loads only the visible ids with full bodies and names a har file`() = runTest {
        // Given a GET and a POST, with the list filtered to POST
        val repository = FakeTransactionRepository(
            listOf(transaction(id = 1, method = "GET"), transaction(id = 2, method = "POST"), transaction(id = 3, method = "POST")),
        )
        val viewModel = createViewModel(repository)
        viewModel.toggleMethodFilter("POST")

        viewModel.harExports.test {
            // When exporting
            viewModel.exportVisibleAsHar()

            // Then only the visible rows were loaded, in list order, never the whole table
            val export = awaitItem()
            assertEquals(listOf(listOf(3L, 2L)), repository.getByIdsCalls)
            assertEquals(0, repository.getAllWithBodiesCalls)

            // And the HAR carries their full bodies
            assertTrue(export.content.contains("request-body-2"))
            assertTrue(export.content.contains("response-body-3"))
            assertFalse(export.content.contains("request-body-1"))

            // And it is shared as a timestamped .har file
            assertTrue(export.fileName, export.fileName.matches(Regex("""netscope-\d{8}-\d{6}\.har""")))
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

    @Test
    fun `statsFilter reports the active search and filters but ignores sort order`() = runTest {
        // Given an unfiltered list
        val viewModel = createViewModel(FakeTransactionRepository(listOf(transaction(id = 1))))

        viewModel.statsFilter.test {
            assertFalse(awaitItem().isFiltered)

            // When only the sort order changes
            viewModel.setSortOrder(SortOrder.DURATION)

            // Then stats are not considered filtered
            expectNoEvents()
            assertFalse(viewModel.statsFilter.value.isFiltered)

            // When a status filter and a search are applied
            viewModel.toggleStatusClassFilter(NetTransaction.StatusClass.CLIENT_ERROR)
            assertEquals("4xx", awaitItem().description)
            viewModel.onSearchQueryChange("api")
            advanceTimeBy(NetScopeViewModel.SEARCH_DEBOUNCE_MS + 1)

            // Then the indicator shows both
            val filter = awaitItem()
            assertTrue(filter.isFiltered)
            assertEquals("\"api\" · 4xx", filter.description)

            // When filters are cleared
            viewModel.clearFilters()

            // Then the indicator goes away
            assertFalse(expectMostRecentItem().isFiltered)
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
private class FakeTransactionRepository(
    initial: List<NetTransaction> = emptyList(),
    private val observeDelayMs: Long = 0L,
) : TransactionRepository {

    /** Every query passed to [observeSummaries], in call order. */
    val summaryQueries = mutableListOf<String>()
    val getByIdsCalls = mutableListOf<List<Long>>()
    var getAllWithBodiesCalls = 0
        private set
    var activeByIdObservers = 0
        private set

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

    override fun observeSummaries(query: String): Flow<List<NetTransaction>> {
        summaryQueries += query
        return rows.map { list ->
            list
                .filter { query.isBlank() || it.url.contains(query, ignoreCase = true) }
                .map { it.copy(requestBody = null, responseBody = null) }
        }
    }

    override fun observeById(id: Long): Flow<NetTransaction?> =
        rows.map { list -> list.firstOrNull { it.id == id } }
            .distinctUntilChanged()
            .onStart {
                activeByIdObservers++
                delay(observeDelayMs)
            }
            .onCompletion { activeByIdObservers-- }

    override suspend fun getById(id: Long): NetTransaction? = rows.value.firstOrNull { it.id == id }

    override suspend fun getAllWithBodies(): List<NetTransaction> {
        getAllWithBodiesCalls++
        return rows.value
    }

    fun remove(id: Long) {
        rows.update { list -> list.filterNot { it.id == id } }
    }

    @Deprecated("See TransactionRepository.observeAll")
    override fun observeAll(): Flow<List<NetTransaction>> = rows

    override suspend fun getByIds(ids: Collection<Long>): List<NetTransaction> {
        getByIdsCalls += ids.toList()
        return rows.value.filter { it.id in ids }
    }

    override suspend fun updateResponseSize(id: Long, bytes: Long) {
        rows.update { list -> list.map { if (it.id == id) it.copy(responseBodySize = bytes) else it } }
    }

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
