package io.netscope.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.netscope.NetScope
import io.netscope.domain.usecase.ClearTransactionsUseCase
import io.netscope.domain.usecase.FilterTransactionsUseCase
import io.netscope.domain.usecase.GetTransactionByIdUseCase
import io.netscope.domain.usecase.SortOrder
import io.netscope.model.NetTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NetScopeViewModel(
    private val filterTransactions: FilterTransactionsUseCase,
    private val getById: GetTransactionByIdUseCase,
    private val clearAll: ClearTransactionsUseCase,
) : ViewModel() {

    // ── Search ────────────────────────────────────────────────────────────────
    val searchQuery = MutableStateFlow("")

    // ── Filter / sort state ───────────────────────────────────────────────────
    val filterState = MutableStateFlow(FilterState())

    // ── Transaction list ──────────────────────────────────────────────────────
    /**
     * Emits the filtered + sorted list whenever the query, filter, or DB changes.
     * Uses [flatMapLatest] so a new upstream Flow is created whenever the filter
     * params change, cancelling the previous collection automatically.
     */
    val transactions: StateFlow<List<NetTransaction>> =
        combine(searchQuery, filterState) { query, filter -> query to filter }
            .flatMapLatest { (query, filter) ->
                filterTransactions(
                    query = query,
                    methods = filter.methods,
                    statusClasses = filter.statusClasses,
                    sortOrder = filter.sortOrder,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Detail ────────────────────────────────────────────────────────────────
    private val _selectedTransaction = MutableStateFlow<NetTransaction?>(null)
    val selectedTransaction: StateFlow<NetTransaction?> = _selectedTransaction

    fun selectTransaction(id: Long) {
        viewModelScope.launch {
            _selectedTransaction.value = getById(id)
        }
    }

    fun clearSelection() {
        _selectedTransaction.value = null
    }

    // ── Actions ───────────────────────────────────────────────────────────────
    fun clearAllTransactions() {
        viewModelScope.launch { clearAll() }
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun toggleMethodFilter(method: String) {
        filterState.value = filterState.value.let { state ->
            val updated = state.methods.toMutableSet()
            if (method in updated) updated.remove(method) else updated.add(method)
            state.copy(methods = updated)
        }
    }

    fun toggleStatusClassFilter(statusClass: NetTransaction.StatusClass) {
        filterState.value = filterState.value.let { state ->
            val updated = state.statusClasses.toMutableSet()
            if (statusClass in updated) updated.remove(statusClass) else updated.add(statusClass)
            state.copy(statusClasses = updated)
        }
    }

    fun setSortOrder(order: SortOrder) {
        filterState.value = filterState.value.copy(sortOrder = order)
    }

    fun clearFilters() {
        filterState.value = FilterState()
        searchQuery.value = ""
    }

    // ── Factory ───────────────────────────────────────────────────────────────
    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repo = NetScope.repository
            return NetScopeViewModel(
                filterTransactions = FilterTransactionsUseCase(repo),
                getById = GetTransactionByIdUseCase(repo),
                clearAll = ClearTransactionsUseCase(repo),
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
