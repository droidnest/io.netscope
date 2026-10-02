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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class NetScopeViewModel(
    private val filterTransactionsUseCase: FilterTransactionsUseCase,
    private val observeTransactionUseCase: ObserveTransactionUseCase,
    private val exportTransactionsUseCase: ExportTransactionsUseCase,
    private val clearTransactionsUseCase: ClearTransactionsUseCase,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    // ── Search ────────────────────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // ── Filter / sort state ───────────────────────────────────────────────────
    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    // ── Transaction list ──────────────────────────────────────────────────────
    /**
     * Emits the filtered + sorted list whenever the query, filter, or DB changes.
     * Rows come from the list projection: [NetTransaction.requestBody] and
     * [NetTransaction.responseBody] are always null here.
     */
    val transactions: StateFlow<List<NetTransaction>> =
        combine(_searchQuery, _filterState) { query, filter -> query to filter }
            .flatMapLatest { (query, filter) -> filteredTransactions(query, filter) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Stats ─────────────────────────────────────────────────────────────────
    /** Aggregates over the filtered list, computed off the main thread. Null until the first result. */
    internal val stats: StateFlow<StatsData?> =
        transactions
            .map { StatsData.compute(it) }
            .flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ── Detail ────────────────────────────────────────────────────────────────
    private val selectedId = MutableStateFlow<Long?>(null)

    /** Live view of the selected transaction (bodies included); re-emits as its response lands. */
    val selectedTransaction: StateFlow<NetTransaction?> =
        selectedId
            .flatMapLatest { id ->
                // Emit null first so a previously selected row never flashes while the new one loads.
                if (id == null) flowOf(null) else observeTransactionUseCase(id).onStart { emit(null) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectTransaction(id: Long) {
        selectedId.value = id
    }

    fun clearSelection() {
        selectedId.value = null
    }

    // ── Export ────────────────────────────────────────────────────────────────
    private val _harExports = Channel<String>(Channel.BUFFERED)

    /** One-shot HAR documents ready to be shared; each is delivered to exactly one collector. */
    val harExports: Flow<String> = _harExports.receiveAsFlow()

    /**
     * Builds a HAR of the currently visible (filtered + sorted) transactions with their
     * bodies loaded, entirely off the main thread, and emits it on [harExports].
     */
    fun exportVisibleAsHar() {
        viewModelScope.launch {
            val visible = filteredTransactions(_searchQuery.value, _filterState.value).first()
            val har = exportTransactionsUseCase { all ->
                val withBodies = all.associateBy { it.id }
                NetScopeExporter.toHar(visible.mapNotNull { withBodies[it.id] })
            }
            _harExports.send(har)
        }
    }

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
}

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
