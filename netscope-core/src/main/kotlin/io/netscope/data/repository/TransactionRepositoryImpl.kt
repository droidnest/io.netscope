package io.netscope.data.repository

import io.netscope.NetScopeConfig
import io.netscope.data.db.TransactionDao
import io.netscope.data.mapper.TransactionMapper.toDomain
import io.netscope.data.mapper.TransactionMapper.toEntity
import io.netscope.data.mapper.TransactionMapper.toEntityWithoutTiming
import io.netscope.domain.repository.TransactionRepository
import io.netscope.model.NetTransaction
import io.netscope.model.TrafficSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    override fun observeSummary(): Flow<TrafficSummary> = dao.observeSummary()

    override suspend fun getById(id: Long): NetTransaction? =
        dao.getById(id)?.toDomain()

    override fun observeById(id: Long): Flow<NetTransaction?> =
        dao.observeById(id = id).map { entity -> entity?.toDomain() }

    override suspend fun updateTiming(
        id: Long, dnsMs: Long?, connectMs: Long?, sslMs: Long?, ttfbMs: Long?,
    ) = dao.updateTiming(id, dnsMs, connectMs, sslMs, ttfbMs)

    override suspend fun clearAll() = dao.deleteAll()
}
