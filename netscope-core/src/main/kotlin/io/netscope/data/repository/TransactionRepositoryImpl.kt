package io.netscope.data.repository

import io.netscope.NetScopeConfig
import io.netscope.data.db.TransactionDao
import io.netscope.data.mapper.TransactionMapper.toDomain
import io.netscope.data.mapper.TransactionMapper.toEntity
import io.netscope.data.mapper.TransactionMapper.toResponseUpdate
import io.netscope.domain.repository.TransactionRepository
import io.netscope.model.NetTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

internal class TransactionRepositoryImpl(
    private val dao: TransactionDao,
    /** Written from the UI thread by `NetScope.updateConfig`, read on IO by [insert]. */
    @Volatile internal var config: NetScopeConfig,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TransactionRepository {

    // ── Writes ────────────────────────────────────────────────────────────────

    override suspend fun insert(transaction: NetTransaction): Long {
        val id = dao.insert(transaction.toEntity())
        dao.pruneOldest(config.maxTransactions)
        return id
    }

    override suspend fun update(transaction: NetTransaction) =
        dao.updateResponse(transaction.toResponseUpdate())

    override suspend fun updateTiming(
        id: Long, dnsMs: Long?, connectMs: Long?, sslMs: Long?, ttfbMs: Long?,
    ) = dao.updateTiming(id, dnsMs, connectMs, sslMs, ttfbMs)

    override suspend fun clearAll() = dao.deleteAll()

    // ── Reads ─────────────────────────────────────────────────────────────────

    override fun observeAll(query: String): Flow<List<NetTransaction>> =
        dao.observeList(query.trim().escapeLike())
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeById(id: Long): Flow<NetTransaction?> =
        dao.observeById(id)
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)

    override suspend fun getById(id: Long): NetTransaction? =
        dao.getById(id)?.toDomain()

    override suspend fun getAllWithBodies(): List<NetTransaction> =
        dao.getAll().map { it.toDomain() }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Escapes LIKE wildcards so user input matches literally (paired with `ESCAPE '\'`). */
    private fun String.escapeLike(): String =
        replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
