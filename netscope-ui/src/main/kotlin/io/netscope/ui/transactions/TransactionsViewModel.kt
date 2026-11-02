package io.netscope.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.netscope.NetScope
import io.netscope.domain.usecase.stats.ComputeTransactionStatsUseCase
import io.netscope.domain.usecase.transaction.ClearTransactionsUseCase
import io.netscope.domain.usecase.transaction.FilterTransactionsUseCase
import io.netscope.model.NetTransaction
import io.netscope.ui.mvi.BaseViewModel
import io.netscope.ui.mvi.UiIntent
import io.netscope.ui.mvi.UiState
import io.netscope.ui.transactions.model.TransactionFilterUi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The flow of the transaction list and the Stats screen, scoped to the navigation graph.
 *
 * As the legacy screens: the Stats screen shows the figures of the filtered list, so the two
 * screens share this ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TransactionsViewModel(
    private val filterTransactionsUseCase: FilterTransactionsUseCase,
    private val computeTransactionStatsUseCase: ComputeTransactionStatsUseCase,
    private val clearTransactionsUseCase: ClearTransactionsUseCase,
    private val defaultDispatcher: CoroutineDispatcher,
) : BaseViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(TransactionFilterUi())
    private val openMenu = MutableStateFlow<TransactionsMenu?>(null)
    private val openedTransactionId = MutableStateFlow<Long?>(null)

    // Filtering and the stats run once per new list, off the main thread, however many intents arrive
    private val contents = combine(query, filter) { query, filter -> query to filter }
        .flatMapLatest { (query, filter) ->
            filterTransactionsUseCase(
                query = query,
                methods = filter.methods,
                statusClasses = filter.statusClasses,
                sortOrder = filter.sortOrder,
            )
        }
        .map { transactions -> contentOf(transactions = transactions) }
        .flowOn(defaultDispatcher)

    val uiState: StateFlow<UiState<TransactionsUiState>> =
        combine(contents, query, filter, openMenu, openedTransactionId) { loaded, query, filter, menu, openedId ->
            val content = loaded.copy(query = query, filter = filter, openMenu = menu)
            UiState.Success(
                data = when (openedId) {
                    null -> TransactionsUiState.Content(content = content)
                    else -> TransactionsUiState.Navigating(content = content, openedTransactionId = openedId)
                },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = UiState.Success(
                data = TransactionsUiState.Content(content = contentOf(transactions = emptyList())),
            ),
        )

    override fun onIntent(intent: UiIntent) {
        if (intent !is TransactionsUiIntent) return
        when (intent) {
            is TransactionsUiIntent.QueryTyped -> query.value = intent.query
            is TransactionsUiIntent.MethodToggled -> filter.update {
                it.copy(methods = it.methods.toggled(item = intent.method))
            }
            is TransactionsUiIntent.StatusClassToggled -> filter.update {
                it.copy(statusClasses = it.statusClasses.toggled(item = intent.statusClass))
            }
            is TransactionsUiIntent.SortOrderSelected -> {
                filter.update { it.copy(sortOrder = intent.sortOrder) }
                openMenu.value = null
            }
            is TransactionsUiIntent.MenuClicked -> openMenu.value = intent.menu
            TransactionsUiIntent.MenuDismissed -> openMenu.value = null
            TransactionsUiIntent.ClearFiltersClicked -> {
                filter.value = TransactionFilterUi()
                query.value = ""
            }
            TransactionsUiIntent.ClearAllClicked -> viewModelScope.launch { clearTransactionsUseCase() }
            is TransactionsUiIntent.TransactionClicked -> openedTransactionId.value = intent.id
            TransactionsUiIntent.NavigationHandled -> openedTransactionId.value = null
        }
    }

    private fun contentOf(transactions: List<NetTransaction>) = TransactionsContentUi(
        transactions = transactions,
        stats = computeTransactionStatsUseCase(transactions = transactions),
    )

    private fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = NetScope.repository
            return TransactionsViewModel(
                filterTransactionsUseCase = FilterTransactionsUseCase(repository = repository),
                computeTransactionStatsUseCase = ComputeTransactionStatsUseCase(),
                clearTransactionsUseCase = ClearTransactionsUseCase(repository = repository),
                defaultDispatcher = Dispatchers.Default,
            ) as T
        }
    }
}
