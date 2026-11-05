package io.netscope.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.netscope.NetScope
import io.netscope.domain.usecase.transaction.ObserveTransactionUseCase
import io.netscope.model.NetTransaction
import io.netscope.ui.mvi.BaseViewModel
import io.netscope.ui.mvi.UiIntent
import io.netscope.ui.mvi.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Follows one transaction live: a pending request fills in as soon as its response is saved. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TransactionDetailViewModel(
    private val observeTransactionUseCase: ObserveTransactionUseCase,
) : BaseViewModel() {

    private val transactionId = MutableStateFlow<Long?>(null)
    private val selection = MutableStateFlow(Selection())
    private var isStarted = false

    val uiState: StateFlow<UiState<TransactionDetailUiState>> = transactionId
        .flatMapLatest { id ->
            if (id == null) flowOf(UiState.Idle) else observedState(id = id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = UiState.Idle,
        )

    override fun onIntent(intent: UiIntent) {
        if (intent !is TransactionDetailUiIntent) return
        when (intent) {
            is TransactionDetailUiIntent.Started -> if (!isStarted) {
                isStarted = true
                transactionId.value = intent.transactionId
            }
            is TransactionDetailUiIntent.TabClicked -> selection.update { it.copy(tab = intent.tab) }
            TransactionDetailUiIntent.MenuClicked -> selection.update { it.copy(isMenuOpen = true) }
            TransactionDetailUiIntent.MenuDismissed -> selection.update { it.copy(isMenuOpen = false) }
            TransactionDetailUiIntent.ErrorShown -> transactionId.value = null
        }
    }

    private fun observedState(id: Long) =
        combine(observeTransactionUseCase(id = id), selection) { transaction, selection ->
            transaction?.toState(selection = selection) ?: UiState.Error(message = MESSAGE_DELETED)
        }

    private fun NetTransaction.toState(selection: Selection) = UiState.Success(
        data = TransactionDetailUiState(
            transaction = this,
            selectedTab = selection.tab,
            isMenuOpen = selection.isMenuOpen,
        ),
    )

    private data class Selection(val tab: Int = 0, val isMenuOpen: Boolean = false)

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TransactionDetailViewModel(
                observeTransactionUseCase = ObserveTransactionUseCase(repository = NetScope.repository),
            ) as T
    }

    private companion object {
        const val MESSAGE_DELETED = "This request is no longer stored."
    }
}
