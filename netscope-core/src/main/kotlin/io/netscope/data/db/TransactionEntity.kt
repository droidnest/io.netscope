package io.netscope.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.netscope.model.NetHeader

@Entity(tableName = "transactions")
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

class HeaderListConverter {
    private val gson = Gson()
    private val type = object : TypeToken<List<NetHeader>>() {}.type

    @TypeConverter
    fun fromJson(value: String): List<NetHeader> = gson.fromJson(value, type)

    @TypeConverter
    fun toJson(value: List<NetHeader>): String = gson.toJson(value)
}
