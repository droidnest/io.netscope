package io.netscope.domain.repository

import io.netscope.model.NetTransaction
import io.netscope.model.TrafficSummary
import io.netscope.model.TransactionSummary
import kotlinx.coroutines.flow.Flow

/** No-op stub matching the real [TransactionRepository] in netscope-core. */
interface TransactionRepository {
    suspend fun insert(transaction: NetTransaction): Long
    suspend fun update(transaction: NetTransaction)
    fun observeAll(): Flow<List<NetTransaction>>
    fun observeSummaries(query: String = ""): Flow<List<TransactionSummary>>
    fun observeSummary(): Flow<TrafficSummary>
    suspend fun getById(id: Long): NetTransaction?
    suspend fun getByIds(ids: List<Long>): List<NetTransaction>
    fun observeById(id: Long): Flow<NetTransaction?>
    suspend fun updateTiming(id: Long, dnsMs: Long?, connectMs: Long?, sslMs: Long?, ttfbMs: Long?)
    suspend fun clearAll()
}
