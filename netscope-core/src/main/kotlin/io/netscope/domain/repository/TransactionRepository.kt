package io.netscope.domain.repository

import io.netscope.model.NetTransaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    /** Inserts a transaction and returns its generated ID. */
    suspend fun insert(transaction: NetTransaction): Long

    /**
     * Persists the response fields of an existing transaction (matched by [NetTransaction.id]).
     * Never touches the network-phase timing columns written by [updateTiming].
     */
    suspend fun update(transaction: NetTransaction)

    /**
     * Emits transactions newest-first on every change.
     *
     * Bodies are NOT loaded ([NetTransaction.requestBody] and [NetTransaction.responseBody]
     * are always null) so each emission stays small. [query] is matched in the database
     * against URL, method, status code, request headers and both bodies.
     */
    fun observeAll(query: String = ""): Flow<List<NetTransaction>>

    /** Emits the full transaction (bodies included) and re-emits whenever it changes. */
    fun observeById(id: Long): Flow<NetTransaction?>

    /** Returns a single transaction by ID, or null if not found. */
    suspend fun getById(id: Long): NetTransaction?

    /** One-shot read of every transaction with bodies, newest-first. Use for exports only. */
    suspend fun getAllWithBodies(): List<NetTransaction>

    /** Updates only the network-phase timing columns (DNS / Connect / TLS / TTFB). */
    suspend fun updateTiming(id: Long, dnsMs: Long?, connectMs: Long?, sslMs: Long?, ttfbMs: Long?)

    /** Removes all stored transactions. */
    suspend fun clearAll()
}
