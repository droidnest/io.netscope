package io.netscope.ui.viewmodel

import androidx.compose.ui.graphics.Color
import io.netscope.model.NetTransaction
import io.netscope.ui.component.ColorAmber
import io.netscope.ui.component.ColorError
import io.netscope.ui.component.ColorSuccess

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
    val durationBuckets: List<DurationBucket>,
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

            // Duration buckets
            val b0   = txList.count { (it.durationMs ?: 0L) < 100L }
            val b100 = txList.count { (it.durationMs ?: 0L) in 100L..299L }
            val b300 = txList.count { (it.durationMs ?: 0L) in 300L..999L }
            val b1s  = txList.count { (it.durationMs ?: 0L) >= 1000L }
            val buckets = listOf(
                DurationBucket("< 100ms",   b0,   ColorSuccess),
                DurationBucket("100–300ms", b100, ColorSuccess.copy(alpha = 0.7f)),
                DurationBucket("300ms–1s",  b300, ColorAmber),
                DurationBucket("> 1s",      b1s,  ColorError),
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
                slowestRequests   = slowest,
                topHosts          = topHosts,
                p50Ms             = p50,
                p95Ms             = p95,
            )
        }

        private fun empty() = StatsData(0, 0f, 0, null, 0L, emptyList(),
            emptyList(), emptyList(), emptyList(), emptyList(), null, null)
    }
}

internal data class DurationBucket(val label: String, val count: Int, val color: Color)
