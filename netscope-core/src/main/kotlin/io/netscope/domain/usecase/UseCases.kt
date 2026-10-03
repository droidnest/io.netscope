package io.netscope.domain.usecase

import io.netscope.domain.repository.TransactionRepository
import io.netscope.model.NetTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Live list of transactions newest-first. Bodies are not loaded — see [TransactionRepository.observeSummaries]. */
class GetTransactionsUseCase(private val repository: TransactionRepository) {
    operator fun invoke(): Flow<List<NetTransaction>> = repository.observeSummaries()
}

class GetTransactionByIdUseCase(private val repository: TransactionRepository) {
    suspend operator fun invoke(id: Long): NetTransaction? = repository.getById(id)
}

/** Live view of one transaction, bodies included — re-emits when its response or timing lands. */
class ObserveTransactionUseCase(private val repository: TransactionRepository) {
    operator fun invoke(id: Long): Flow<NetTransaction?> = repository.observeById(id)
}

class ClearTransactionsUseCase(private val repository: TransactionRepository) {
    suspend operator fun invoke() = repository.clearAll()
}

/**
 * Loads every transaction with bodies and formats it off the main thread.
 * [format] is supplied by the caller (e.g. `NetScopeExporter::toHar`) so core stays export-agnostic.
 */
class ExportTransactionsUseCase(
    private val repository: TransactionRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /** Exports every stored transaction. */
    suspend operator fun invoke(format: (List<NetTransaction>) -> String): String {
        val transactions = repository.getAllWithBodies()
        return withContext(defaultDispatcher) { format(transactions) }
    }

    /** Exports only [ids] (e.g. the rows currently visible), loading just those bodies, in [ids] order. */
    suspend operator fun invoke(ids: List<Long>, format: (List<NetTransaction>) -> String): String {
        val byId = repository.getByIds(ids).associateBy { it.id }
        val transactions = ids.mapNotNull { byId[it] }
        return withContext(defaultDispatcher) { format(transactions) }
    }
}

/**
 * Applies search query, method/status filters, and sort order to the live transaction stream.
 * The text search runs in the database; filtering and sorting run on [defaultDispatcher].
 * All filter sets are "pass-all" when empty.
 */
class FilterTransactionsUseCase(
    private val repository: TransactionRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    operator fun invoke(
        query: String = "",
        methods: Set<String> = emptySet(),
        statusClasses: Set<NetTransaction.StatusClass> = emptySet(),
        sortOrder: SortOrder = SortOrder.TIME,
    ): Flow<List<NetTransaction>> = repository.observeSummaries(query.trim())
        .map { list ->
            list
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
        .flowOn(defaultDispatcher)
}

/** Ordering applied to the filtered transaction list. */
enum class SortOrder { TIME, DURATION, SIZE }
