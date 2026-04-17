package io.netscope.data.repository

import io.netscope.NetScopeConfig
import io.netscope.data.db.TransactionDao
import io.netscope.data.mapper.TransactionMapper.toDomain
import io.netscope.data.mapper.TransactionMapper.toEntity
import io.netscope.domain.repository.TransactionRepository
import io.netscope.model.NetTransaction
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
        dao.update(transaction.toEntity())
    }

    override fun observeAll(): Flow<List<NetTransaction>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: Long): NetTransaction? =
        dao.getById(id)?.toDomain()

    override suspend fun updateTiming(
        id: Long, dnsMs: Long?, connectMs: Long?, sslMs: Long?, ttfbMs: Long?,
    ) = dao.updateTiming(id, dnsMs, connectMs, sslMs, ttfbMs)

    override suspend fun clearAll() = dao.deleteAll()
}
