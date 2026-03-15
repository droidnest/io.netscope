package io.netscope.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.netscope.NetScope
import io.netscope.domain.usecase.ClearTransactionsUseCase
import io.netscope.domain.usecase.GetTransactionByIdUseCase
import io.netscope.domain.usecase.GetTransactionsUseCase
import io.netscope.model.NetTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NetScopeViewModel(
    private val getTransactions: GetTransactionsUseCase,
    private val getById: GetTransactionByIdUseCase,
    private val clearAll: ClearTransactionsUseCase,
) : ViewModel() {

    // ── Search / filter state ─────────────────────────────────────────────────
    val searchQuery = MutableStateFlow("")

    // ── Transaction list ──────────────────────────────────────────────────────
    val transactions: StateFlow<List<NetTransaction>> =
        combine(getTransactions(), searchQuery) { list, query ->
            if (query.isBlank()) list
            else list.filter { it.matchesQuery(query) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun NetTransaction.matchesQuery(query: String): Boolean {
        val q = query.lowercase()
        return url.lowercase().contains(q) ||
                method.lowercase().contains(q) ||
                responseCode?.toString()?.contains(q) == true ||
                requestBody?.lowercase()?.contains(q) == true ||
                responseBody?.lowercase()?.contains(q) == true ||
                requestHeaders.any { it.name.lowercase().contains(q) || it.value.lowercase().contains(q) }
    }

    // ── Factory ───────────────────────────────────────────────────────────────
    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repo = NetScope.repository
            return NetScopeViewModel(
                getTransactions = GetTransactionsUseCase(repo),
                getById = GetTransactionByIdUseCase(repo),
                clearAll = ClearTransactionsUseCase(repo),
            ) as T
        }
    }
}
