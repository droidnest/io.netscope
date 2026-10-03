package io.netscope.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import io.netscope.model.NetHeader

/**
 * Row of the `transactions` table.
 *
 * `requestDate` is indexed: it drives the newest-first ORDER BY of every list query and the
 * prune that runs after each insert.
 *
 * `requestHeadersText` is a search-only copy of [requestHeaders] as `Name: Value` lines (see
 * [HeaderCodec.toSearchText]); list queries LIKE against it instead of the JSON column.
 */
@Entity(
    tableName = "transactions",
    indices = [Index("requestDate")],
)
@TypeConverters(HeaderListConverter::class)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Timing
    val requestDate: Long,
    val responseDate: Long?,
    val durationMs: Long?,

    // Network phase timing (nullable — null means connection was reused or not measured)
    val dnsMs: Long?,
    val connectMs: Long?,
    val sslMs: Long?,
    val ttfbMs: Long?,

    // Request
    val method: String,
    val scheme: String,
    val host: String,
    val path: String,
    val query: String?,
    val requestHeaders: List<NetHeader>,
    @ColumnInfo(defaultValue = "")
    val requestHeadersText: String = "",
    val requestBody: String?,
    val requestContentType: String?,
    val requestBodySize: Long,

    // Response
    val responseCode: Int?,
    val responseMessage: String?,
    val responseHeaders: List<NetHeader>,
    val responseBody: String?,
    val responseContentType: String?,
    val responseBodySize: Long,

    // Error
    val error: String?,
)

/** Room adapter for header lists; encoding lives in [HeaderCodec] (no HTML escaping). */
class HeaderListConverter {

    @TypeConverter
    fun fromJson(value: String): List<NetHeader> = HeaderCodec.fromJson(value)

    @TypeConverter
    fun toJson(value: List<NetHeader>): String = HeaderCodec.toJson(value)
}
