package io.netscope.model

/** The figures the notification shows, read without loading any transaction. */
data class TrafficSummary(
    /** How many transactions are stored. */
    val requestCount: Int,
    /** How many of them failed or have a 4xx or 5xx response. */
    val errorCount: Int,
    /** Response code of the newest transaction, or null when it has none yet. */
    val lastResponseCode: Int?,
)
