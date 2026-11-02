package io.netscope.model

/** No-op stub matching the real [TrafficSummary] in netscope-core. */
data class TrafficSummary(
    val requestCount: Int,
    val errorCount: Int,
    val lastResponseCode: Int?,
)
