package io.netscope.ui.transactions

import io.netscope.model.NetTransaction
import io.netscope.model.SortOrder
import io.netscope.ui.mvi.UiIntent

internal sealed interface TransactionsUiIntent : UiIntent {
    data class QueryTyped(val query: String) : TransactionsUiIntent
    data class MethodToggled(val method: String) : TransactionsUiIntent
    data class StatusClassToggled(val statusClass: NetTransaction.StatusClass) : TransactionsUiIntent
    data class SortOrderSelected(val sortOrder: SortOrder) : TransactionsUiIntent
    data class MenuClicked(val menu: TransactionsMenu) : TransactionsUiIntent
    data object MenuDismissed : TransactionsUiIntent
    data object ClearFiltersClicked : TransactionsUiIntent
    data object ClearAllClicked : TransactionsUiIntent
    data class TransactionClicked(val id: Long) : TransactionsUiIntent
    data object NavigationHandled : TransactionsUiIntent
    data object ExportClicked : TransactionsUiIntent
    data object ShareHandled : TransactionsUiIntent
}
