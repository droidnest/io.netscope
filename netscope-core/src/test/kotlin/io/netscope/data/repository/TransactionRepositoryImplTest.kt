package io.netscope.data.repository

import io.netscope.NetScopeConfig
import io.netscope.data.db.RequestHeadersRow
import io.netscope.data.db.TransactionDao
import io.netscope.data.db.TransactionEntity
import io.netscope.data.db.TransactionWithoutTiming
import io.netscope.fake.aTransaction
import io.netscope.fake.toSummary
import io.netscope.data.mapper.TransactionMapper.toEntity
import io.netscope.model.NetHeader
import io.netscope.model.NetTransaction
import io.netscope.model.TrafficSummary
import io.netscope.model.TransactionSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionRepositoryImplTest {

    private val first = aTransaction(id = 1L, path = "/users")
    private val second = aTransaction(id = 2L, path = "/orders")
    private val dao = FakeTransactionDao(rows = listOf(first, second))
    private val sut = TransactionRepositoryImpl(dao = dao, config = NetScopeConfig())

    @Test
    fun `should not search when the query is blank`() = runTest {
        // When
        val result = sut.observeSummaries(query = "  ").first()

        // Then
        assertEquals(listOf(first.toSummary(), second.toSummary()), result)
        assertEquals(emptyList<String>(), dao.patterns)
    }

    @Test
    fun `should keep the rows the database matches when searching`() = runTest {
        // Given
        dao.idsMatching = listOf(2L)

        // When
        val result = sut.observeSummaries(query = "orders").first()

        // Then
        assertEquals(listOf(second.toSummary()), result)
        assertEquals(listOf("%orders%"), dao.patterns)
    }

    @Test
    fun `should escape the wildcards of the query when searching`() = runTest {
        // When
        sut.observeSummaries(query = """50%_a\b""").first()

        // Then
        assertEquals(listOf("""%50\%\_a\\b%"""), dao.patterns)
    }

    @Test
    fun `should match a request header ignoring case when searching`() = runTest {
        // Given
        dao.headers = listOf(
            RequestHeadersRow(id = 1L, requestHeaders = listOf(NetHeader(name = "Accept", value = "a=b&c"))),
        )

        // When
        val result = sut.observeSummaries(query = "A=B&C").first()

        // Then
        assertEquals(listOf(first.toSummary()), result)
    }

    @Test
    fun `should cut a query too long for a LIKE pattern when searching`() = runTest {
        // When
        sut.observeSummaries(query = "x".repeat(60_000)).first()

        // Then
        assertEquals(1_002, dao.patterns.single().length)
    }

    @Test
    fun `should not emit again when a write leaves the summaries unchanged`() = runTest {
        // Given
        val emitted = mutableListOf<List<TransactionSummary>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sut.observeSummaries().collect { emitted += it }
        }

        // When
        dao.summaries.value = dao.summaries.value.toList()

        // Then
        assertEquals(1, emitted.size)
    }

    @Test
    fun `should not emit again when a write leaves the transaction unchanged`() = runTest {
        // Given
        val emitted = mutableListOf<NetTransaction?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sut.observeById(id = 1L).collect { emitted += it }
        }

        // When
        dao.observedRow.value = dao.observedRow.value.copy()

        // Then
        assertEquals(1, emitted.size)
    }

    @Test
    fun `should read whole rows in the given order and in chunks when loading many`() = runTest {
        // Given
        val ids = (1_200L downTo 1L).toList()
        val rows = FakeTransactionDao(rows = ids.map { aTransaction(id = it) })
        val repository = TransactionRepositoryImpl(dao = rows, config = NetScopeConfig())

        // When
        val result = repository.getByIds(ids = ids)

        // Then
        assertEquals(ids, result.map { it.id })
        assertEquals(listOf(500, 500, 200), rows.idChunks)
    }

    @Test
    fun `should leave out the ids no longer stored when loading many`() = runTest {
        // When
        val result = sut.getByIds(ids = listOf(2L, 99L, 1L))

        // Then
        assertEquals(listOf(2L, 1L), result.map { it.id })
    }

    private class FakeTransactionDao(private val rows: List<NetTransaction>) : TransactionDao {

        val summaries = MutableStateFlow(rows.map { it.toSummary() })
        val observedRow = MutableStateFlow(rows.first().toEntity())
        val patterns = mutableListOf<String>()
        val idChunks = mutableListOf<Int>()
        var idsMatching = emptyList<Long>()
        var headers = emptyList<RequestHeadersRow>()

        override fun observeSummaries(): Flow<List<TransactionSummary>> = summaries

        override suspend fun idsMatching(pattern: String): List<Long> {
            patterns += pattern
            return idsMatching
        }

        override suspend fun requestHeaders(): List<RequestHeadersRow> = headers

        override suspend fun getByIds(ids: List<Long>): List<TransactionEntity> {
            idChunks += ids.size
            return rows.filter { it.id in ids }.map { it.toEntity() }
        }

        override fun observeById(id: Long): Flow<TransactionEntity?> = observedRow.map { it }

        override suspend fun insert(entity: TransactionEntity): Long = error("Not faked")
        override suspend fun updateWithoutTiming(transaction: TransactionWithoutTiming) = Unit
        override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override fun observeSummary(): Flow<TrafficSummary> = flowOf(TrafficSummary(0, 0, null))
        override suspend fun getById(id: Long): TransactionEntity? = null
        override suspend fun deleteAll() = Unit
        override suspend fun count(): Int = rows.size
        override suspend fun updateTiming(id: Long, dnsMs: Long?, connectMs: Long?, sslMs: Long?, ttfbMs: Long?) = Unit
        override suspend fun pruneOldest(maxCount: Int) = Unit
    }
}
