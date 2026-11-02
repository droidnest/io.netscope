package io.netscope.domain.usecase.transaction

import io.netscope.domain.repository.TransactionRepository
import io.netscope.model.NetTransaction
import io.netscope.model.SortOrder
import io.netscope.model.TransactionSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Applies a search query, method and status filters and a sort order to the live transactions.
 * An empty filter set lets everything through.
 */
class FilterTransactionsUseCase(
    private val repository: TransactionRepository,
) {

    /** Emits the summaries of the matching transactions, in [sortOrder], on every change. */
    operator fun invoke(
        query: String = "",
        methods: Set<String> = emptySet(),
        statusClasses: Set<NetTransaction.StatusClass> = emptySet(),
        sortOrder: SortOrder = SortOrder.TIME,
    ): Flow<List<TransactionSummary>> = repository.observeSummaries(query = query).map { transactions ->
        transactions
            .filter { methods.isEmpty() || it.method.uppercase() in methods }
            .filter { statusClasses.isEmpty() || it.statusClass in statusClasses }
            .sortedBy(sortOrder = sortOrder)
    }

    private fun List<TransactionSummary>.sortedBy(sortOrder: SortOrder): List<TransactionSummary> =
        when (sortOrder) {
            SortOrder.TIME     -> this // The database already returns the newest first
            SortOrder.DURATION -> sortedByDescending { it.durationMs ?: -1L }
            SortOrder.SIZE     -> sortedByDescending { it.totalSize }
        }
}
