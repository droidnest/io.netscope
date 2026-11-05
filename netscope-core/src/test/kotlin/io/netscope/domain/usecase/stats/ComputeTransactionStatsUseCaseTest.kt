package io.netscope.domain.usecase.stats

import io.netscope.fake.aTransaction
import io.netscope.model.NetTransaction.StatusClass
import io.netscope.model.TransactionStats
import io.netscope.model.TransactionStats.DurationBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComputeTransactionStatsUseCaseTest {

    private val sut = ComputeTransactionStatsUseCase()

    @Test
    fun `should return empty figures when there are no transactions`() {
        // When
        val result = sut(transactions = emptyList())

        // Then
        assertEquals(
            TransactionStats(
                totalCount = 0,
                successRate = 0f,
                errorCount = 0,
                avgDurationMs = null,
                totalBytes = 0L,
                methodCounts = emptyList(),
                statusClassCounts = emptyList(),
                durationBuckets = emptyList(),
                slowestRequests = emptyList(),
                topHosts = emptyList(),
                p50Ms = null,
                p95Ms = null,
            ),
            result,
        )
    }

    @Test
    fun `should count 4xx 5xx and failed requests as errors when computing`() {
        // Given
        val transactions = listOf(
            aTransaction(id = 1L, responseCode = 200),
            aTransaction(id = 2L, responseCode = 404),
            aTransaction(id = 3L, responseCode = 503),
            aTransaction(id = 4L, responseCode = null, error = "timeout"),
        )

        // When
        val result = sut(transactions = transactions)

        // Then
        assertEquals(3, result.errorCount)
        assertEquals(0.25f, result.successRate)
    }

    @Test
    fun `should leave pending requests out of the duration figures when computing`() {
        // Given
        val transactions = listOf(
            aTransaction(id = 1L, durationMs = 100L),
            aTransaction(id = 2L, durationMs = 300L),
            aTransaction(id = 3L, responseCode = null, durationMs = null),
        )

        // When
        val result = sut(transactions = transactions)

        // Then
        assertEquals(200L, result.avgDurationMs)
        assertEquals(listOf(DurationBucket.MEDIUM to 1, DurationBucket.SLOW to 1), result.durationBuckets)
        assertEquals(listOf(2L, 1L), result.slowestRequests.map { it.id })
    }

    @Test
    fun `should put each duration in its bucket when it sits on a boundary`() {
        // Given
        val transactions = listOf(99L, 100L, 299L, 300L, 999L, 1_000L)
            .mapIndexed { index, duration -> aTransaction(id = index.toLong(), durationMs = duration) }

        // When
        val result = sut(transactions = transactions)

        // Then
        assertEquals(
            listOf(
                DurationBucket.FAST to 1,
                DurationBucket.MEDIUM to 2,
                DurationBucket.SLOW to 2,
                DurationBucket.VERY_SLOW to 1,
            ),
            result.durationBuckets,
        )
    }

    @Test
    fun `should read the percentiles from the sorted durations when computing`() {
        // Given
        val transactions = (1L..20L).map { aTransaction(id = it, durationMs = it * 10L) }

        // When
        val result = sut(transactions = transactions.reversed())

        // Then
        assertEquals(100L, result.p50Ms)
        assertEquals(190L, result.p95Ms)
    }

    @Test
    fun `should rank hosts methods and status classes by count when computing`() {
        // Given
        val transactions = listOf(
            aTransaction(id = 1L, method = "GET", host = "a.com"),
            aTransaction(id = 2L, method = "POST", host = "b.com", responseCode = 500),
            aTransaction(id = 3L, method = "POST", host = "b.com", responseCode = 500),
        )

        // When
        val result = sut(transactions = transactions)

        // Then
        assertEquals(listOf("b.com" to 2, "a.com" to 1), result.topHosts)
        assertEquals(listOf("POST" to 2, "GET" to 1), result.methodCounts)
        assertEquals(listOf(StatusClass.SERVER_ERROR to 2, StatusClass.SUCCESS to 1), result.statusClassCounts)
    }

    @Test
    fun `should keep five entries when ranking more hosts and requests`() {
        // Given
        val transactions = (1L..7L).map { aTransaction(id = it, host = "host$it.com", durationMs = it) }

        // When
        val result = sut(transactions = transactions)

        // Then
        assertEquals(5, result.topHosts.size)
        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), result.slowestRequests.map { it.id })
    }

    @Test
    fun `should have no average when every request is pending`() {
        // When
        val result = sut(transactions = listOf(aTransaction(responseCode = null, durationMs = null)))

        // Then
        assertNull(result.avgDurationMs)
        assertEquals(emptyList<Pair<DurationBucket, Int>>(), result.durationBuckets)
    }
}
