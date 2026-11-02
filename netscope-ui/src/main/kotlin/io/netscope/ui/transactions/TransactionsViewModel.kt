package io.netscope.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.netscope.NetScope
import io.netscope.domain.usecase.stats.ComputeTransactionStatsUseCase
import io.netscope.domain.usecase.transaction.ClearTransactionsUseCase
import io.netscope.domain.usecase.transaction.FilterTransactionsUseCase
import io.netscope.domain.usecase.transaction.LoadTransactionsUseCase
import io.netscope.export.NetScopeExporter
import io.netscope.model.TransactionSummary
import io.netscope.ui.mvi.BaseViewModel
import io.netscope.ui.mvi.UiIntent
import io.netscope.ui.mvi.UiState
import io.netscope.ui.transactions.model.TransactionFilterUi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The flow of the transaction list and the Stats screen, scoped to the navigation graph.
 *
 * As the legacy screens: the Stats screen shows the figures of the filtered list, so the two
 * screens share this ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class TransactionsViewModel(
    private val filterTransactionsUseCase: FilterTransactionsUseCase,
    private val computeTransactionStatsUseCase: ComputeTransactionStatsUseCase,
    private val clearTransactionsUseCase: ClearTransactionsUseCase,
    private val loadTransactionsUseCase: LoadTransactionsUseCase,
    private val defaultDispatcher: CoroutineDispatcher,
) : BaseViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(TransactionFilterUi())
    private val openMenu = MutableStateFlow<TransactionsMenu?>(null)
    private val pendingAction = MutableStateFlow<PendingAction?>(null)

    // A search reads every body, so it waits for a pause in the typing; clearing it is immediate
    private val searchedQuery = query.debounce { typed -> if (typed.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }

    // Filtering and the stats run once per new list, off the main thread, however many intents arrive
    private val contents = combine(searchedQuery, filter) { query, filter -> query to filter }
        .flatMapLatest { (query, filter) ->
            filterTransactionsUseCase(
                query = query,
                methods = filter.methods,
                statusClasses = filter.statusClasses,
                sortOrder = filter.sortOrder,
            ).throttled()
        }
        .map { transactions -> contentOf(transactions = transactions) }
        .flowOn(defaultDispatcher)

    val uiState: StateFlow<UiState<TransactionsUiState>> =
        combine(contents, query, filter, openMenu, pendingAction) { loaded, query, filter, menu, action ->
            val content = loaded.copy(query = query, filter = filter, openMenu = menu)
            UiState.Success(
                data = when (action) {
                    null -> TransactionsUiState.Content(content = content)
                    is PendingAction.Open ->
                        TransactionsUiState.Navigating(content = content, openedTransactionId = action.id)
                    is PendingAction.Share -> TransactionsUiState.SharingHar(content = content, har = action.har)
                },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = UiState.Success(
                data = TransactionsUiState.Content(
                    content = contentOf(transactions = emptyList()).copy(isLoading = true),
                ),
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
            is TransactionsUiIntent.TransactionClicked -> pendingAction.value = PendingAction.Open(id = intent.id)
            TransactionsUiIntent.NavigationHandled,
            TransactionsUiIntent.ShareHandled -> pendingAction.value = null
            TransactionsUiIntent.ExportClicked -> export()
        }
    }

    /** The list holds no bodies, so an export reads them, away from the main thread. */
    private fun export() {
        openMenu.value = null
        val ids = uiState.value.dataOrNull()?.content?.transactions.orEmpty().map { it.id }
        viewModelScope.launch {
            val har = withContext(defaultDispatcher) { NetScopeExporter.toHar(loadTransactionsUseCase(ids = ids)) }
            pendingAction.value = PendingAction.Share(har = har)
        }
    }

    /** Lets the first value through, then the latest at most once per [LIST_UPDATE_MS]. */
    private fun <T> Flow<T>.throttled(): Flow<T> = conflate().transform { value ->
        emit(value)
        delay(LIST_UPDATE_MS)
    }

    private fun contentOf(transactions: List<TransactionSummary>) = TransactionsContentUi(
        transactions = transactions,
        stats = computeTransactionStatsUseCase(transactions = transactions),
    )

    private fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

    /** What the screen has to do once, then report back. */
    private sealed interface PendingAction {
        data class Open(val id: Long) : PendingAction
        data class Share(val har: String) : PendingAction
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = NetScope.repository
            return TransactionsViewModel(
                filterTransactionsUseCase = FilterTransactionsUseCase(repository = repository),
                computeTransactionStatsUseCase = ComputeTransactionStatsUseCase(),
                clearTransactionsUseCase = ClearTransactionsUseCase(repository = repository),
                loadTransactionsUseCase = LoadTransactionsUseCase(repository = repository),
                defaultDispatcher = Dispatchers.Default,
            ) as T
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        const val LIST_UPDATE_MS = 250L
    }
}
