package io.netscope.domain.usecase.transaction

import io.netscope.domain.repository.TransactionRepository
import io.netscope.model.NetTransaction

/** Reads whole transactions, bodies included, for an export of a list of summaries. */
class LoadTransactionsUseCase(
    private val repository: TransactionRepository,
) {

    /** Returns the stored transactions with these [ids], in the same order. */
    suspend operator fun invoke(ids: List<Long>): List<NetTransaction> = repository.getByIds(ids = ids)
}
