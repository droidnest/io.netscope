package io.netscope.ui.transactions

import io.netscope.model.NetTransaction
import io.netscope.model.TransactionStats
import io.netscope.ui.transactions.model.TransactionFilterUi

/** State of the transaction list and the Stats screen, which shows figures of the same list. */
internal sealed interface TransactionsUiState {

    val content: TransactionsContentUi

    data class Content(override val content: TransactionsContentUi) : TransactionsUiState

    data class Navigating(
        override val content: TransactionsContentUi,
        val openedTransactionId: Long,
    ) : TransactionsUiState
}

internal data class TransactionsContentUi(
    val transactions: List<NetTransaction>,
    val stats: TransactionStats,
    val query: String = "",
    val filter: TransactionFilterUi = TransactionFilterUi(),
    val openMenu: TransactionsMenu? = null,
) {
    val isFiltering: Boolean
        get() = filter.isActive || query.isNotBlank()
}

/** The drop-down menus of the transaction list. */
internal enum class TransactionsMenu { SORT, EXPORT }
