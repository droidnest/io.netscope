package io.netscope.domain.repository

import io.netscope.model.NetTransaction
import io.netscope.model.TrafficSummary
import kotlinx.coroutines.flow.Flow

/** The captured transactions, stored in the NetScope database. */
interface TransactionRepository {
    /** Inserts a transaction and returns its generated ID. */
    suspend fun insert(transaction: NetTransaction): Long

    /**
     * Updates an existing transaction (matched by [NetTransaction.id]). The network-phase
     * timing columns are left as they are: [updateTiming] owns them.
     */
    suspend fun update(transaction: NetTransaction)

    /** Emits the full list of transactions, sorted newest-first, on every change. */
    fun observeAll(): Flow<List<NetTransaction>>

    /** Emits the [TrafficSummary] of the stored transactions on every change. */
    fun observeSummary(): Flow<TrafficSummary>

    /** Returns a single transaction by ID, or null if not found. */
    suspend fun getById(id: Long): NetTransaction?

    /** Emits the transaction with this [id] on every change, or null once it is deleted. */
    fun observeById(id: Long): Flow<NetTransaction?>

    /** Updates only the network-phase timing columns (DNS / Connect / TLS / TTFB). */
    suspend fun updateTiming(id: Long, dnsMs: Long?, connectMs: Long?, sslMs: Long?, ttfbMs: Long?)

    /** Removes all stored transactions. */
    suspend fun clearAll()
}
