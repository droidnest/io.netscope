package io.netscope.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.netscope.NetScope
import io.netscope.domain.usecase.ClearTransactionsUseCase
import io.netscope.domain.usecase.ExportTransactionsUseCase
import io.netscope.domain.usecase.FilterTransactionsUseCase
import io.netscope.domain.usecase.ObserveTransactionUseCase
import io.netscope.domain.usecase.SortOrder
import io.netscope.export.NetScopeExporter
import io.netscope.model.NetTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class NetScopeViewModel(
    private val filterTransactionsUseCase: FilterTransactionsUseCase,
    private val observeTransactionUseCase: ObserveTransactionUseCase,
    private val exportTransactionsUseCase: ExportTransactionsUseCase,
    private val clearTransactionsUseCase: ClearTransactionsUseCase,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    // ── Search ────────────────────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")

    /** Raw text-field value; updates on every keystroke. */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Query actually sent to the database: trimmed, debounced while typing so a burst of
     * keystrokes runs one LIKE scan over the body columns instead of one per key.
     * Clearing the field applies immediately.
     */
    private val effectiveQuery: Flow<String> = _searchQuery
        .map { it.trim() }
        .debounce { query -> if (query.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
        .distinctUntilChanged()

    // ── Filter / sort state ───────────────────────────────────────────────────
    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    // ── Transaction list ──────────────────────────────────────────────────────
    /**
     * Emits the filtered + sorted list whenever the (debounced) query, filter, or DB changes.
     * Filter chips and sort apply immediately; only the text search is debounced.
     * Rows come from the list projection: [NetTransaction.requestBody] and
     * [NetTransaction.responseBody] are always null here.
     */
    val transactions: StateFlow<List<NetTransaction>> =
        combine(effectiveQuery, _filterState) { query, filter -> query to filter }
            .flatMapLatest { (query, filter) -> filteredTransactions(query, filter) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Stats ─────────────────────────────────────────────────────────────────
    /** Aggregates over the filtered list, computed off the main thread. Null until the first result. */
    internal val stats: StateFlow<StatsData?> =
        transactions
            .map { StatsData.compute(it) }
            .flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The filter [stats] were computed under, so the Stats tab can say it is showing a subset. */
    internal val statsFilter: StateFlow<StatsFilter> =
        combine(effectiveQuery, _filterState) { query, filter ->
            StatsFilter(query = query, methods = filter.methods, statusClasses = filter.statusClasses)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsFilter())

    // ── Detail ────────────────────────────────────────────────────────────────
    private var selectedId: Long? = null
    private var detailJob: Job? = null
    private val _detailState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)

    /**
     * What the detail screen shows for the selected id:
     * - [DetailUiState.Loading] synchronously as soon as a *different* id is selected (never a
     *   stale row from the previous id), until the first DB result for it;
     * - [DetailUiState.Loaded] on every emission of the row (bodies included);
     * - [DetailUiState.Missing] when the row is absent (pruned, cleared, or never existed).
     *
     * The DB is observed only while someone collects this flow (5 s stop timeout, like
     * `WhileSubscribed(5_000)`), but the last value is kept per id, so resubscribing for the
     * same id — e.g. returning from background — never regresses to Loading or loses scroll.
     */
    val detailState: StateFlow<DetailUiState> = _detailState.asStateFlow()

    /** Selects [id] for [detailState]. Idempotent: re-selecting the current id keeps its state. */
    fun selectTransaction(id: Long) {
        if (id == selectedId) return
        selectedId = id
        _detailState.value = DetailUiState.Loading
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            SharingStarted.WhileSubscribed(5_000)
                .command(_detailState.subscriptionCount)
                .distinctUntilChanged()
                .collectLatest { command ->
                    if (command == SharingCommand.START) {
                        observeTransactionUseCase(id).collect { tx ->
                            _detailState.value = if (tx == null) DetailUiState.Missing else DetailUiState.Loaded(tx)
                        }
                    }
                }
        }
    }

    fun clearSelection() {
        selectedId = null
        detailJob?.cancel()
        detailJob = null
        _detailState.value = DetailUiState.Loading
    }

    // ── Export ────────────────────────────────────────────────────────────────
    private val _harExports = Channel<HarExport>(Channel.BUFFERED)

    /** One-shot HAR documents ready to be shared as a file; each is delivered to exactly one collector. */
    val harExports: Flow<HarExport> = _harExports.receiveAsFlow()

    /**
     * Builds a HAR of the currently visible (filtered + sorted) transactions, loading bodies
     * for those rows only, entirely off the main thread, and emits it on [harExports].
     */
    fun exportVisibleAsHar() {
        viewModelScope.launch {
            val query = _searchQuery.value.trim()
            val visibleIds = filteredTransactions(query, _filterState.value).first().map { it.id }
            val har = exportTransactionsUseCase(visibleIds, NetScopeExporter::toHar)
            _harExports.send(HarExport(fileName = harFileName(), content = har))
        }
    }

    private fun harFileName(): String =
        "netscope-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(clock()))}.har"

    // ── Actions ───────────────────────────────────────────────────────────────
    fun clearAllTransactions() {
        viewModelScope.launch { clearTransactionsUseCase() }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleMethodFilter(method: String) {
        _filterState.update { state ->
            val updated = if (method in state.methods) state.methods - method else state.methods + method
            state.copy(methods = updated)
        }
    }

    fun toggleStatusClassFilter(statusClass: NetTransaction.StatusClass) {
        _filterState.update { state ->
            val updated = if (statusClass in state.statusClasses) state.statusClasses - statusClass
                          else state.statusClasses + statusClass
            state.copy(statusClasses = updated)
        }
    }

    fun setSortOrder(order: SortOrder) {
        _filterState.update { it.copy(sortOrder = order) }
    }

    fun clearFilters() {
        _filterState.value = FilterState()
        _searchQuery.value = ""
    }

    private fun filteredTransactions(query: String, filter: FilterState): Flow<List<NetTransaction>> =
        filterTransactionsUseCase(
            query         = query,
            methods       = filter.methods,
            statusClasses = filter.statusClasses,
            sortOrder     = filter.sortOrder,
        )

    // ── Factory ───────────────────────────────────────────────────────────────
    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = NetScope.repository
            return NetScopeViewModel(
                filterTransactionsUseCase = FilterTransactionsUseCase(repository),
                observeTransactionUseCase = ObserveTransactionUseCase(repository),
                exportTransactionsUseCase = ExportTransactionsUseCase(repository),
                clearTransactionsUseCase  = ClearTransactionsUseCase(repository),
            ) as T
        }
    }

    internal companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

/** A HAR document to share as a file (too large for `EXTRA_TEXT`). */
data class HarExport(val fileName: String, val content: String)

/**
 * Snapshot of the active filter selections.
 * All sets are "pass-all" when empty.
 */
data class FilterState(
    val methods: Set<String> = emptySet(),
    val statusClasses: Set<NetTransaction.StatusClass> = emptySet(),
    val sortOrder: SortOrder = SortOrder.TIME,
) {
    val isActive: Boolean
        get() = methods.isNotEmpty() || statusClasses.isNotEmpty() || sortOrder != SortOrder.TIME
}
