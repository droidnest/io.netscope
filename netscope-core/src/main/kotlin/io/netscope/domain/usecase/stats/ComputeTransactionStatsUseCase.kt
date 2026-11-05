package io.netscope.domain.usecase.stats

import io.netscope.model.NetTransaction
import io.netscope.model.TransactionStats
import io.netscope.model.TransactionStats.DurationBucket

/** Aggregates a list of transactions into the figures of the Stats screen. */
class ComputeTransactionStatsUseCase {

    /** Returns the figures of [transactions], all zero or empty when there are none. */
    operator fun invoke(transactions: List<NetTransaction>): TransactionStats {
        val durations = transactions.mapNotNull { it.durationMs }.sorted()
        return TransactionStats(
            totalCount        = transactions.size,
            successRate       = transactions.successRate(),
            errorCount        = transactions.count { it.statusClass in ERROR_CLASSES },
            avgDurationMs     = if (durations.isEmpty()) null else durations.average().toLong(),
            totalBytes        = transactions.sumOf { it.totalSize },
            methodCounts      = transactions.countsBy { it.method },
            statusClassCounts = transactions.countsBy { it.statusClass },
            durationBuckets   = durations.buckets(),
            slowestRequests   = transactions.filter { it.durationMs != null }
                .sortedByDescending { it.durationMs }
                .take(RANKING_SIZE),
            topHosts          = transactions.countsBy { it.host }.take(RANKING_SIZE),
            p50Ms             = durations.percentile(percent = 50),
            p95Ms             = durations.percentile(percent = 95),
        )
    }

    private fun List<NetTransaction>.successRate(): Float {
        if (isEmpty()) return 0f
        return count { it.statusClass == NetTransaction.StatusClass.SUCCESS }.toFloat() / size
    }

    private fun <K> List<NetTransaction>.countsBy(key: (NetTransaction) -> K): List<Pair<K, Int>> =
        groupingBy(key).eachCount().entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

    private fun List<Long>.buckets(): List<Pair<DurationBucket, Int>> =
        groupingBy { it.bucket() }.eachCount().toList().sortedBy { (bucket, _) -> bucket.ordinal }

    private fun Long.bucket(): DurationBucket = when {
        this < 100L  -> DurationBucket.FAST
        this < 300L  -> DurationBucket.MEDIUM
        this < 1000L -> DurationBucket.SLOW
        else         -> DurationBucket.VERY_SLOW
    }

    /** Nearest rank: the value at the smallest position that covers [percent] of the list. */
    private fun List<Long>.percentile(percent: Int): Long? =
        if (isEmpty()) null else this[(size * percent + 99) / 100 - 1]

    private companion object {
        const val RANKING_SIZE = 5
        val ERROR_CLASSES = setOf(
            NetTransaction.StatusClass.CLIENT_ERROR,
            NetTransaction.StatusClass.SERVER_ERROR,
            NetTransaction.StatusClass.ERROR,
        )
    }
}
