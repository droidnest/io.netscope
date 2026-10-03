package io.netscope.data.db

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.netscope.model.NetHeader
import java.lang.reflect.Type

/**
 * Single source of truth for how header lists are stored.
 *
 * - JSON columns (`requestHeaders`, `responseHeaders`) are written WITHOUT Gson's default HTML
 *   escaping, so `charset=utf-8` is stored verbatim instead of as `charset=utf-8`. Rows
 *   written by older versions (escaped) still parse: both forms are valid JSON.
 * - The search column (`requestHeadersText`) holds one `Name: Value` line per header so a LIKE
 *   never matches JSON syntax or keys (`"name"`, `"value"`, `true`). Redacted headers arrive
 *   with their value already masked, so no secret ends up in this column.
 */
internal object HeaderCodec {

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    private val listType: Type = object : TypeToken<List<NetHeader>>() {}.type

    fun fromJson(json: String): List<NetHeader> = gson.fromJson(json, listType) ?: emptyList()

    fun toJson(headers: List<NetHeader>): String = gson.toJson(headers, listType)

    /** `"Name: Value\n"` per header; the value of the `requestHeadersText` search column. */
    fun toSearchText(headers: List<NetHeader>): String =
        headers.joinToString(separator = "") { "${it.name}: ${it.value}\n" }
}
