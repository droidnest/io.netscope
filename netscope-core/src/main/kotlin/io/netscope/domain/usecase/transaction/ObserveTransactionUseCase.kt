package io.netscope.domain.usecase.transaction

import io.netscope.domain.repository.TransactionRepository
import io.netscope.model.NetTransaction
import kotlinx.coroutines.flow.Flow

/** Follows one transaction, so its response and timing appear as soon as they are saved. */
class ObserveTransactionUseCase(
    private val repository: TransactionRepository,
) {

    /** Emits the transaction with this [id] on every change, or null once it is deleted. */
    operator fun invoke(id: Long): Flow<NetTransaction?> = repository.observeById(id = id)
}
