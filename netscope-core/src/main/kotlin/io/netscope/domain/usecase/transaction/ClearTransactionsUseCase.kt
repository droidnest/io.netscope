package io.netscope.domain.usecase.transaction

import io.netscope.domain.repository.TransactionRepository

/** Deletes every captured transaction. */
class ClearTransactionsUseCase(
    private val repository: TransactionRepository,
) {

    /** Deletes every captured transaction. */
    suspend operator fun invoke() = repository.clearAll()
}
