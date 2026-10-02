package io.netscope.interceptor

import io.netscope.domain.repository.TransactionRepository
import io.netscope.model.NetTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/** In-memory [TransactionRepository] that records every insert/update for assertions. */
internal class FakeTransactionRepository : TransactionRepository {

    val inserted = CopyOnWriteArrayList<NetTransaction>()
    val updated = CopyOnWriteArrayList<NetTransaction>()

    private val nextId = AtomicLong(1)
    private val rows = MutableStateFlow<Map<Long, NetTransaction>>(emptyMap())

    /** Latest stored state of [id] (insert overlaid by updates). */
    fun row(id: Long): NetTransaction? = rows.value[id]

    override suspend fun insert(transaction: NetTransaction): Long {
        val id = nextId.getAndIncrement()
        inserted += transaction
        rows.value = rows.value + (id to transaction.copy(id = id))
        return id
    }

    override suspend fun update(transaction: NetTransaction) {
        updated += transaction
        rows.value = rows.value + (transaction.id to transaction)
    }

    override fun observeAll(query: String): Flow<List<NetTransaction>> =
        rows.map { all ->
            all.values
                .filter { query.isEmpty() || it.url.contains(query, ignoreCase = true) }
                .sortedByDescending { it.id }
                .map { it.copy(requestBody = null, responseBody = null) }
        }

    override fun observeById(id: Long): Flow<NetTransaction?> = rows.map { it[id] }

    override suspend fun getById(id: Long): NetTransaction? = rows.value[id]

    override suspend fun getAllWithBodies(): List<NetTransaction> =
        rows.value.values.sortedByDescending { it.id }

    override suspend fun updateTiming(id: Long, dnsMs: Long?, connectMs: Long?, sslMs: Long?, ttfbMs: Long?) {
        val row = rows.value[id] ?: return
        rows.value = rows.value + (id to row.copy(dnsMs = dnsMs, connectMs = connectMs, sslMs = sslMs, ttfbMs = ttfbMs))
    }

    override suspend fun clearAll() {
        rows.value = emptyMap()
    }
}
