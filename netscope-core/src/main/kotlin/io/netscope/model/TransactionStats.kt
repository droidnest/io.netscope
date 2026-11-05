package io.netscope.model

/** Aggregated figures over a list of captured transactions, shown by the Stats screen. */
data class TransactionStats(
    /** Number of transactions. */
    val totalCount: Int,
    /** Share of 2xx responses, from 0 to 1. */
    val successRate: Float,
    /** Number of 4xx, 5xx and failed requests. */
    val errorCount: Int,
    /** Average duration of the completed requests, or null when none completed. */
    val avgDurationMs: Long?,
    /** Sum of request and response body sizes. */
    val totalBytes: Long,
    /** Requests per HTTP method, most used first. */
    val methodCounts: List<Pair<String, Int>>,
    /** Requests per status class, most frequent first. */
    val statusClassCounts: List<Pair<NetTransaction.StatusClass, Int>>,
    /** Completed requests per duration range, empty ranges left out. */
    val durationBuckets: List<Pair<DurationBucket, Int>>,
    /** The five slowest completed requests, slowest first. */
    val slowestRequests: List<TransactionSummary>,
    /** The five most called hosts, most called first. */
    val topHosts: List<Pair<String, Int>>,
    /** Median duration of the completed requests. */
    val p50Ms: Long?,
    /** 95th percentile duration of the completed requests. */
    val p95Ms: Long?,
) {

    /** Duration ranges of [durationBuckets]. */
    enum class DurationBucket {
        /** Under 100 ms. */
        FAST,

        /** 100 to 299 ms. */
        MEDIUM,

        /** 300 to 999 ms. */
        SLOW,

        /** One second or more. */
        VERY_SLOW,
    }
}
