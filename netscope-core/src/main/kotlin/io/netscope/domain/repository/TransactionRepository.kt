package io.netscope.domain.repository

import io.netscope.model.NetTransaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    /** Inserts a transaction and returns its generated ID. */
    suspend fun insert(transaction: NetTransaction): Long

    /** Updates an existing transaction (matched by [NetTransaction.id]). */
    suspend fun update(transaction: NetTransaction)

    /** Emits the full list of transactions, sorted newest-first, on every change. */
    fun observeAll(): Flow<List<NetTransaction>>

    /** Returns a single transaction by ID, or null if not found. */
    suspend fun getById(id: Long): NetTransaction?

    /** Removes all stored transactions. */
    suspend fun clearAll()
}
