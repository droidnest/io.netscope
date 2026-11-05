package io.netscope.ui.transactions

import io.netscope.model.TransactionStats
import io.netscope.model.TransactionSummary
import io.netscope.ui.transactions.model.TransactionFilterUi

/** State of the transaction list and the Stats screen, which shows figures of the same list. */
internal sealed interface TransactionsUiState {

    val content: TransactionsContentUi

    data class Content(override val content: TransactionsContentUi) : TransactionsUiState

    data class Navigating(
        override val content: TransactionsContentUi,
        val openedTransactionId: Long,
    ) : TransactionsUiState

    /** The export is ready for the screen to hand to the share sheet. */
    data class SharingHar(
        override val content: TransactionsContentUi,
        val har: String,
    ) : TransactionsUiState
}

internal data class TransactionsContentUi(
    val transactions: List<TransactionSummary>,
    val stats: TransactionStats,
    /** True until the first list arrives, when an empty one says nothing about the traffic. */
    val isLoading: Boolean = false,
    val query: String = "",
    val filter: TransactionFilterUi = TransactionFilterUi(),
    val openMenu: TransactionsMenu? = null,
) {
    val isFiltering: Boolean
        get() = filter.isActive || query.isNotBlank()
}

/** The drop-down menus of the transaction list. */
internal enum class TransactionsMenu { SORT, EXPORT }
