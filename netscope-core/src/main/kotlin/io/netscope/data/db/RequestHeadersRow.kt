package io.netscope.data.db

import io.netscope.model.NetHeader

/** The request headers of one transaction, which a search compares once decoded. */
data class RequestHeadersRow(
    val id: Long,
    val requestHeaders: List<NetHeader>,
)
