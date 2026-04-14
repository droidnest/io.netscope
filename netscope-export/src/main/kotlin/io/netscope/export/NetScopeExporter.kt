package io.netscope.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.google.gson.GsonBuilder
import io.netscope.model.NetHeader
import io.netscope.model.NetTransaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Utility object for exporting NetScope transactions.
 *
 * Provides:
 * - [toCurl]           — generate a cURL command string for a single transaction
 * - [toHar]            — generate an HTTP Archive (HAR 1.2) JSON for a list of transactions
 * - [copyToClipboard]  — copy text to the system clipboard
 * - [share]            — share text via the Android share sheet
 */
object NetScopeExporter {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    // ── cURL ──────────────────────────────────────────────────────────────────

    /**
     * Generates a cURL command that reproduces [transaction].
     *
     * Example output:
     * ```
     * curl -X POST 'https://api.example.com/v1/login' \
     *   -H 'Content-Type: application/json' \
     *   -H 'Authorization: ██████' \
     *   --data-raw '{"email":"test@example.com"}'
     * ```
     */
    fun toCurl(transaction: NetTransaction): String = buildString {
        append("curl -X ${transaction.method}")
        append(" '${transaction.url}'")

        transaction.requestHeaders.forEach { header ->
            append(" \\\n  -H '${header.name}: ${header.value}'")
        }

        transaction.requestBody?.let { body ->
            val escaped = body.replace("'", "'\\''")
            append(" \\\n  --data-raw '$escaped'")
        }
    }

    // ── HAR ───────────────────────────────────────────────────────────────────

    /**
     * Generates an [HTTP Archive 1.2](https://www.softwareishard.com/blog/har-12-spec/)
     * JSON string from a list of transactions.
     *
     * The resulting file can be imported into Charles, Proxyman, or browser
     * DevTools for further analysis.
     */
    fun toHar(transactions: List<NetTransaction>): String {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val entries = transactions.map { tx ->
            mapOf(
                "startedDateTime" to isoFormat.format(Date(tx.requestDate)),
                "time" to (tx.durationMs ?: -1),
                "request" to mapOf(
                    "method" to tx.method,
                    "url" to tx.url,
                    "httpVersion" to "HTTP/1.1",
                    "headers" to tx.requestHeaders.toHarHeaders(),
                    "queryString" to parseQueryString(tx.query),
                    "postData" to tx.requestBody?.let {
                        mapOf(
                            "mimeType" to (tx.requestContentType ?: "text/plain"),
                            "text" to it,
                        )
                    },
                    "headersSize" to -1,
                    "bodySize" to tx.requestBodySize,
                ),
                "response" to mapOf(
                    "status" to (tx.responseCode ?: 0),
                    "statusText" to (tx.responseMessage ?: ""),
                    "httpVersion" to "HTTP/1.1",
                    "headers" to tx.responseHeaders.toHarHeaders(),
                    "content" to mapOf(
                        "size" to tx.responseBodySize,
                        "mimeType" to (tx.responseContentType ?: "application/octet-stream"),
                        "text" to (tx.responseBody ?: ""),
                    ),
                    "redirectURL" to "",
                    "headersSize" to -1,
                    "bodySize" to tx.responseBodySize,
                ),
                "cache" to emptyMap<String, Any>(),
                "timings" to mapOf(
                    "dns" to (tx.dnsMs ?: -1),
                    "connect" to (tx.connectMs ?: -1),
                    "ssl" to (tx.sslMs ?: -1),
                    "send" to 0,
                    "wait" to (tx.ttfbMs ?: -1),
                    "receive" to -1,
                ),
            )
        }

        val har = mapOf(
            "log" to mapOf(
                "version" to "1.2",
                "creator" to mapOf(
                    "name" to "NetScope",
                    "version" to "0.3.0",
                ),
                "entries" to entries,
            ),
        )

        return gson.toJson(har)
    }

    // ── Android helpers ───────────────────────────────────────────────────────

    /**
     * Copies [text] to the system clipboard with the given [label].
     */
    fun copyToClipboard(context: Context, text: String, label: String = "NetScope") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /**
     * Opens the Android share sheet to share [text] with other apps.
     *
     * @param chooserTitle Optional title shown on the share chooser dialog.
     */
    fun share(context: Context, text: String, chooserTitle: String = "Share via") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun List<NetHeader>.toHarHeaders(): List<Map<String, String>> =
        map { mapOf("name" to it.name, "value" to it.value) }

    private fun parseQueryString(query: String?): List<Map<String, String>> {
        if (query.isNullOrBlank()) return emptyList()
        return query.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) null
            else mapOf(
                "name" to pair.substring(0, idx),
                "value" to pair.substring(idx + 1),
            )
        }
    }
}
