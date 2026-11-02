package io.netscope.ui.transactions.model

import io.netscope.model.NetTransaction
import io.netscope.model.SortOrder

/** The active filter selections; an empty set lets everything through. */
internal data class TransactionFilterUi(
    val methods: Set<String> = emptySet(),
    val statusClasses: Set<NetTransaction.StatusClass> = emptySet(),
    val sortOrder: SortOrder = SortOrder.TIME,
) {
    val isActive: Boolean
        get() = methods.isNotEmpty() || statusClasses.isNotEmpty() || sortOrder != SortOrder.TIME
}
