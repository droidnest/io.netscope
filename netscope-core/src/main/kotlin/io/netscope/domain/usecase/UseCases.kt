package io.netscope.domain.usecase

import io.netscope.domain.repository.TransactionRepository
import io.netscope.model.NetTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetTransactionsUseCase(private val repository: TransactionRepository) {
    operator fun invoke(): Flow<List<NetTransaction>> = repository.observeAll()
}

class GetTransactionByIdUseCase(private val repository: TransactionRepository) {
    suspend operator fun invoke(id: Long): NetTransaction? = repository.getById(id)
}

class ClearTransactionsUseCase(private val repository: TransactionRepository) {
    suspend operator fun invoke() = repository.clearAll()
}

/**
 * Applies search query, method/status filters, and sort order to the live transaction stream.
 * All filter sets are "pass-all" when empty.
 */
class FilterTransactionsUseCase(private val repository: TransactionRepository) {
    operator fun invoke(
        query: String = "",
        methods: Set<String> = emptySet(),
        statusClasses: Set<NetTransaction.StatusClass> = emptySet(),
        sortOrder: SortOrder = SortOrder.TIME,
    ): Flow<List<NetTransaction>> = repository.observeAll().map { list ->
        list
            .filter { tx -> query.isBlank() || tx.matchesQuery(query) }
            .filter { tx -> methods.isEmpty() || tx.method.uppercase() in methods }
            .filter { tx -> statusClasses.isEmpty() || tx.statusClass in statusClasses }
            .let { filtered ->
                when (sortOrder) {
                    SortOrder.TIME     -> filtered                                               // DB already returns newest-first
                    SortOrder.DURATION -> filtered.sortedByDescending { it.durationMs ?: -1L }
                    SortOrder.SIZE     -> filtered.sortedByDescending { it.totalSize }
                }
            }
    }

    private fun NetTransaction.matchesQuery(query: String): Boolean {
        val q = query.lowercase()
        return url.lowercase().contains(q) ||
                method.lowercase().contains(q) ||
                responseCode?.toString()?.contains(q) == true ||
                requestBody?.lowercase()?.contains(q) == true ||
                responseBody?.lowercase()?.contains(q) == true ||
                requestHeaders.any { it.name.lowercase().contains(q) || it.value.lowercase().contains(q) }
    }
}

/** Ordering applied to the filtered transaction list. */
enum class SortOrder { TIME, DURATION, SIZE }
