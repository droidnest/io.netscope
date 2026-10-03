package io.netscope.ui.viewmodel

import io.netscope.model.NetTransaction
import io.netscope.ui.component.ColorAmber
import io.netscope.ui.component.ColorError
import io.netscope.ui.component.ColorSuccess
import io.netscope.ui.component.durationColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsDataTest {

    @Test
    fun `pending rows are excluded from duration buckets and their percentage denominator`() {
        // Given two completed rows and two pending ones
        val stats = StatsData.compute(
            listOf(
                transaction(id = 1, durationMs = 50),
                transaction(id = 2, durationMs = 1_500),
                transaction(id = 3, responseCode = null, durationMs = null),
                transaction(id = 4, responseCode = null, durationMs = null),
            ),
        )

        // Then only the completed rows are bucketed, and they are the denominator
        assertEquals(listOf("< 100ms" to 1, "≥ 1s" to 1), stats.durationBuckets.map { it.label to it.count })
        assertEquals(2, stats.timedCount)
        assertEquals(4, stats.totalCount)
    }

    @Test
    fun `failed rows with a duration are bucketed`() {
        // Given a network failure that took 400 ms
        val stats = StatsData.compute(
            listOf(transaction(id = 1, responseCode = null, durationMs = 400, error = "timeout")),
        )

        // Then it lands in the 300ms–1s bucket
        assertEquals(listOf("300ms–1s" to 1), stats.durationBuckets.map { it.label to it.count })
        assertEquals(1, stats.timedCount)
    }

    @Test
    fun `bucket boundaries match the shared duration colours`() {
        // Given rows at each threshold
        val stats = StatsData.compute(
            listOf(
                transaction(id = 1, durationMs = 99),
                transaction(id = 2, durationMs = 100),
                transaction(id = 3, durationMs = 299),
                transaction(id = 4, durationMs = 300),
                transaction(id = 5, durationMs = 999),
                transaction(id = 6, durationMs = 1_000),
            ),
        )

        // Then 300 ms is amber in both the buckets and the colour function
        assertEquals(
            listOf("< 100ms" to 1, "100–300ms" to 2, "300ms–1s" to 2, "≥ 1s" to 1),
            stats.durationBuckets.map { it.label to it.count },
        )
        assertEquals(ColorSuccess, durationColor(299))
        assertEquals(ColorAmber, durationColor(300))
        assertEquals(ColorAmber, durationColor(999))
        assertEquals(ColorError, durationColor(1_000))
    }

    @Test
    fun `only pending rows give no buckets`() {
        val stats = StatsData.compute(listOf(transaction(id = 1, responseCode = null, durationMs = null)))

        assertTrue(stats.durationBuckets.isEmpty())
        assertEquals(0, stats.timedCount)
    }

    @Test
    fun `StatsFilter describes query, methods and status classes`() {
        assertFalse(StatsFilter().isFiltered)
        assertEquals("", StatsFilter().description)

        val filter = StatsFilter(
            query = "users",
            methods = setOf("POST", "GET"),
            statusClasses = setOf(NetTransaction.StatusClass.SERVER_ERROR, NetTransaction.StatusClass.CLIENT_ERROR),
        )

        assertTrue(filter.isFiltered)
        assertEquals("\"users\" · GET, POST · 4xx, 5xx", filter.description)
    }

    private fun transaction(
        id: Long,
        responseCode: Int? = 200,
        durationMs: Long? = 100L,
        error: String? = null,
    ) = NetTransaction(
        id                 = id,
        requestDate        = 1_000L * id,
        durationMs         = durationMs,
        method             = "GET",
        scheme             = "https",
        host               = "api.example.com",
        path               = "/items/$id",
        query              = null,
        requestHeaders     = emptyList(),
        requestBody        = null,
        requestContentType = null,
        requestBodySize    = 0L,
        responseCode       = responseCode,
        error              = error,
    )
}
