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
     * Emits transactions newest-first on every change, for lists, stats and notifications.
     *
     * Bodies are NOT loaded ([NetTransaction.requestBody] and [NetTransaction.responseBody]
     * are always null) so each emission stays small. [query] is matched in the database
     * against URL, method, status code, request header names/values and both bodies.
     */
    fun observeSummaries(query: String = ""): Flow<List<NetTransaction>>

    /** Emits every transaction with bodies, newest-first, on every change. */
    @Deprecated(
        "Re-reads every body on each write and can exhaust memory. Use observeSummaries() for lists " +
            "and observeById() / getAllWithBodies() / getByIds() when bodies are needed.",
        ReplaceWith("observeSummaries()"),
    )
    fun observeAll(): Flow<List<NetTransaction>>

    /** Emits the full transaction (bodies included) and re-emits whenever it changes. */
    fun observeById(id: Long): Flow<NetTransaction?>

    /** Returns a single transaction by ID, or null if not found. */
    suspend fun getById(id: Long): NetTransaction?

    /** One-shot read of every transaction with bodies, newest-first. Use for exports only. */
    suspend fun getAllWithBodies(): List<NetTransaction>

    /** One-shot read of the given transactions with bodies, newest-first. Unknown ids are skipped. */
    suspend fun getByIds(ids: Collection<Long>): List<NetTransaction>

    /** Records the real number of response body bytes the app read (known only once the body is consumed). */
    suspend fun updateResponseSize(id: Long, bytes: Long)

    /** Updates only the network-phase timing columns (DNS / Connect / TLS / TTFB). */
    suspend fun updateTiming(id: Long, dnsMs: Long?, connectMs: Long?, sslMs: Long?, ttfbMs: Long?)

    /** Removes all stored transactions. */
    suspend fun clearAll()
}
