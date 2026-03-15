package io.netscope.domain.usecase

import io.netscope.domain.repository.TransactionRepository
import io.netscope.model.NetTransaction
import kotlinx.coroutines.flow.Flow

class GetTransactionsUseCase(private val repository: TransactionRepository) {
    operator fun invoke(): Flow<List<NetTransaction>> = repository.observeAll()
}

class GetTransactionByIdUseCase(private val repository: TransactionRepository) {
    suspend operator fun invoke(id: Long): NetTransaction? = repository.getById(id)
}

class ClearTransactionsUseCase(private val repository: TransactionRepository) {
    suspend operator fun invoke() = repository.clearAll()
}
