package io.netscope.data.repository

import io.netscope.NetScopeConfig
import io.netscope.data.db.TransactionDao
import io.netscope.data.mapper.TransactionMapper.toDomain
import io.netscope.data.mapper.TransactionMapper.toEntity
import io.netscope.data.mapper.TransactionMapper.toEntityWithoutTiming
import io.netscope.domain.repository.TransactionRepository
import io.netscope.model.NetHeader
import io.netscope.model.NetTransaction
import io.netscope.model.TrafficSummary
import io.netscope.model.TransactionSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

@OptIn(ExperimentalCoroutinesApi::class)
internal class TransactionRepositoryImpl(
    private val dao: TransactionDao,
    internal var config: NetScopeConfig,
) : TransactionRepository {

    override suspend fun insert(transaction: NetTransaction): Long {
        val id = dao.insert(transaction.toEntity())
        dao.pruneOldest(config.maxTransactions)
        return id
    }

    override suspend fun update(transaction: NetTransaction) {
        dao.updateWithoutTiming(transaction.toEntityWithoutTiming())
    }

    override fun observeAll(): Flow<List<NetTransaction>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeSummaries(query: String): Flow<List<TransactionSummary>> =
        if (query.isBlank()) {
            dao.observeSummaries().distinctUntilChanged()
        } else {
            dao.observeSummaries()
                .mapLatest { summaries ->
                    val matching = idsMatching(query = query.take(MAX_QUERY_CHARS))
                    summaries.filter { it.id in matching }
                }
                .distinctUntilChanged()
        }

    override fun observeSummary(): Flow<TrafficSummary> = dao.observeSummary()

    override suspend fun getById(id: Long): NetTransaction? =
        dao.getById(id)?.toDomain()

    override suspend fun getByIds(ids: List<Long>): List<NetTransaction> {
        val stored = ids.chunked(size = MAX_IDS_PER_QUERY)
            .flatMap { chunk -> dao.getByIds(ids = chunk) }
            .associateBy { it.id }
        return ids.mapNotNull { id -> stored[id]?.toDomain() }
    }

    // A write to any transaction wakes this flow: only a change to this one reaches the collector
    override fun observeById(id: Long): Flow<NetTransaction?> =
        dao.observeById(id = id).map { entity -> entity?.toDomain() }.distinctUntilChanged()

    override suspend fun updateTiming(
        id: Long, dnsMs: Long?, connectMs: Long?, sslMs: Long?, ttfbMs: Long?,
    ) = dao.updateTiming(id, dnsMs, connectMs, sslMs, ttfbMs)

    override suspend fun clearAll() = dao.deleteAll()

    private suspend fun idsMatching(query: String): Set<Long> {
        val inColumns = dao.idsMatching(pattern = "%${query.escapedForLike()}%")
        val inHeaders = dao.requestHeaders()
            .filter { row -> row.requestHeaders.any { it.contains(query = query) } }
            .map { it.id }
        return (inColumns + inHeaders).toSet()
    }

    private fun NetHeader.contains(query: String): Boolean =
        name.contains(query, ignoreCase = true) || value.contains(query, ignoreCase = true)

    private fun String.escapedForLike(): String =
        replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private companion object {
        /** Well under SQLite's 50,000 byte limit on a LIKE pattern. */
        const val MAX_QUERY_CHARS = 1_000

        /** Under the 999 bound parameters of SQLite before 3.32, which old Android versions ship. */
        const val MAX_IDS_PER_QUERY = 500
    }
}
