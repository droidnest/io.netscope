package io.netscope.ui.viewmodel

import androidx.compose.ui.graphics.Color
import io.netscope.model.NetTransaction
import io.netscope.ui.component.ColorAmber
import io.netscope.ui.component.ColorError
import io.netscope.ui.component.ColorSuccess
import io.netscope.ui.component.DURATION_FAST_MS
import io.netscope.ui.component.DURATION_SLOW_MS

// ── Stats model ───────────────────────────────────────────────────────────────

/**
 * Aggregated analytics for a transaction list.
 * Built off the main thread by [NetScopeViewModel.stats]; never compute it in composition.
 */
internal data class StatsData(
    val totalCount: Int,
    val successRate: Float,              // 0..1
    val errorCount: Int,                 // 4xx + 5xx + ERROR
    val avgDurationMs: Long?,
    val totalBytes: Long,
    val methodCounts: List<Pair<String, Int>>,
    val statusClassCounts: List<Pair<NetTransaction.StatusClass, Int>>,
    /** Only rows with a duration (completed or failed); pending rows are excluded. */
    val durationBuckets: List<DurationBucket>,
    /** Number of rows bucketed in [durationBuckets] — the denominator for their percentages. */
    val timedCount: Int,
    val slowestRequests: List<NetTransaction>,
    val topHosts: List<Pair<String, Int>>,
    val p50Ms: Long?,
    val p95Ms: Long?,
) {
    companion object {
        private val ERROR_CLASSES = setOf(
            NetTransaction.StatusClass.CLIENT_ERROR,
            NetTransaction.StatusClass.SERVER_ERROR,
            NetTransaction.StatusClass.ERROR,
        )

        fun compute(txList: List<NetTransaction>): StatsData {
            if (txList.isEmpty()) return empty()

            val total = txList.size
            val successCount = txList.count { it.statusClass == NetTransaction.StatusClass.SUCCESS }
            val errorCount = txList.count { it.statusClass in ERROR_CLASSES }

            val durations = txList.mapNotNull { it.durationMs }.sorted()
            val avgMs     = if (durations.isEmpty()) null else durations.average().toLong()
            val p50       = if (durations.isEmpty()) null else durations[durations.size * 50 / 100]
            val p95       = if (durations.isEmpty()) null else durations[(durations.size * 95 / 100).coerceAtMost(durations.size - 1)]

            // Method counts (sorted descending)
            val methodCounts = txList.groupingBy { it.method }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .map { it.key to it.value }

            // Status class counts (sorted descending)
            val statusCounts = txList.groupingBy { it.statusClass }
                .eachCount()
                .entries
                .filter { it.value > 0 }
                .sortedByDescending { it.value }
                .map { it.key to it.value }

            // Duration buckets — pending rows have no duration and must not land in "< 100ms".
            val b0   = durations.count { it < FAST_BUCKET_MS }
            val b100 = durations.count { it in FAST_BUCKET_MS until DURATION_FAST_MS }
            val b300 = durations.count { it in DURATION_FAST_MS until DURATION_SLOW_MS }
            val b1s  = durations.count { it >= DURATION_SLOW_MS }
            val buckets = listOf(
                DurationBucket("< 100ms",   b0,   ColorSuccess),
                DurationBucket("100–300ms", b100, ColorSuccess.copy(alpha = 0.7f)),
                DurationBucket("300ms–1s",  b300, ColorAmber),
                DurationBucket("≥ 1s",      b1s,  ColorError),
            ).filter { it.count > 0 }

            // Slowest 5 completed
            val slowest = txList
                .filter { it.durationMs != null }
                .sortedByDescending { it.durationMs }
                .take(5)

            // Top 5 hosts
            val topHosts = txList.groupingBy { it.host }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key to it.value }

            return StatsData(
                totalCount        = total,
                successRate       = successCount.toFloat() / total,
                errorCount        = errorCount,
                avgDurationMs     = avgMs,
                totalBytes        = txList.sumOf { it.totalSize },
                methodCounts      = methodCounts,
                statusClassCounts = statusCounts,
                durationBuckets   = buckets,
                timedCount        = durations.size,
                slowestRequests   = slowest,
                topHosts          = topHosts,
                p50Ms             = p50,
                p95Ms             = p95,
            )
        }

        private const val FAST_BUCKET_MS = 100L

        private fun empty() = StatsData(0, 0f, 0, null, 0L, emptyList(),
            emptyList(), emptyList(), 0, emptyList(), emptyList(), null, null)
    }
}

internal data class DurationBucket(val label: String, val count: Int, val color: Color)

/**
 * The list filter that [StatsData] was computed under (the Stats tab mirrors the Requests list).
 * Sort order is ignored: it does not change the aggregates.
 */
internal data class StatsFilter(
    val query: String = "",
    val methods: Set<String> = emptySet(),
    val statusClasses: Set<NetTransaction.StatusClass> = emptySet(),
) {
    val isFiltered: Boolean
        get() = query.isNotBlank() || methods.isNotEmpty() || statusClasses.isNotEmpty()

    /** Short human-readable summary, e.g. `"api" · GET, POST · 4xx`. Empty when not filtered. */
    val description: String
        get() = listOfNotNull(
            query.takeIf { it.isNotBlank() }?.let { "\"$it\"" },
            methods.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(", "),
            statusClasses.takeIf { it.isNotEmpty() }?.sortedBy { it.ordinal }?.joinToString(", ") { it.shortLabel() },
        ).joinToString(" · ")
}

private fun NetTransaction.StatusClass.shortLabel(): String = when (this) {
    NetTransaction.StatusClass.SUCCESS      -> "2xx"
    NetTransaction.StatusClass.REDIRECT     -> "3xx"
    NetTransaction.StatusClass.CLIENT_ERROR -> "4xx"
    NetTransaction.StatusClass.SERVER_ERROR -> "5xx"
    NetTransaction.StatusClass.ERROR        -> "ERR"
    NetTransaction.StatusClass.PENDING      -> "Pending"
}
