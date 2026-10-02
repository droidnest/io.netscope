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
 * - [headersText]      — format a header list as `Name: Value` lines
 * - [toFullSummary]    — human-readable full transaction dump (good for bug reports)
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
     * curl -X 'POST' 'https://api.example.com/v1/login' \
     *   -H 'Content-Type: application/json' \
     *   -H 'Authorization: ██████' \
     *   --data-raw '{"email":"test@example.com"}'
     * ```
     *
     * Every argument taken from the transaction (method, URL, headers, body) is single-quoted
     * for POSIX shells, so pasting the command can never execute anything else.
     */
    fun toCurl(transaction: NetTransaction): String = buildString {
        append("curl -X ${transaction.method.shellQuote()}")
        append(" ${transaction.url.shellQuote()}")

        transaction.requestHeaders.forEach { header ->
            append(" \\\n  -H ${"${header.name}: ${header.value}".shellQuote()}")
        }

        transaction.requestBody?.let { body ->
            append(" \\\n  --data-raw ${body.shellQuote()}")
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
                    "version" to BuildConfig.VERSION_NAME,
                ),
                "entries" to entries,
            ),
        )

        return gson.toJson(har)
    }

    // ── Plain-text formatters ─────────────────────────────────────────────────

    /**
     * Formats [headers] as `Name: Value` lines, one per line.
     * Redacted headers keep their `██████` placeholder.
     */
    fun headersText(headers: List<NetHeader>): String =
        headers.joinToString("\n") { "${it.name}: ${it.value}" }

    /**
     * Returns a human-readable full transaction dump suitable for pasting
     * into a bug report, Slack message, or JIRA ticket.
     *
     * Sections present only when data is available:
     * endpoint, timing, request headers, request body,
     * response status + headers, response body, error.
     */
    fun toFullSummary(tx: NetTransaction): String = buildString {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

        appendLine("═══════════════════════════════════════")
        appendLine("  ${tx.method}  ${tx.url}")
        tx.responseCode?.let { code ->
            append("  $code ${tx.responseMessage ?: ""}")
            tx.durationMs?.let { append("  ·  ${it}ms") }
            appendLine()
        }
        appendLine("  ${dateFmt.format(Date(tx.requestDate))}")
        appendLine("═══════════════════════════════════════")

        if (tx.requestHeaders.isNotEmpty()) {
            appendLine()
            appendLine("── REQUEST HEADERS (${tx.requestHeaders.size})")
            appendLine(headersText(tx.requestHeaders))
        }

        tx.requestBody?.let { body ->
            appendLine()
            appendLine("── REQUEST BODY")
            appendLine(body)
        }

        if (tx.responseHeaders.isNotEmpty()) {
            appendLine()
            appendLine("── RESPONSE HEADERS (${tx.responseHeaders.size})")
            appendLine(headersText(tx.responseHeaders))
        }

        tx.responseBody?.let { body ->
            appendLine()
            appendLine("── RESPONSE BODY")
            appendLine(body)
        }

        tx.error?.let { err ->
            appendLine()
            appendLine("── ERROR")
            appendLine(err)
        }

        // Timing phases (only when EventListener data is present)
        val hasPhases = tx.dnsMs != null || tx.connectMs != null ||
                tx.sslMs != null || tx.ttfbMs != null
        if (hasPhases) {
            appendLine()
            appendLine("── NETWORK PHASES")
            tx.dnsMs?.let     { appendLine("  DNS:     ${it}ms") }
            tx.connectMs?.let { appendLine("  Connect: ${it}ms") }
            tx.sslMs?.let     { appendLine("  TLS:     ${it}ms") }
            tx.ttfbMs?.let    { appendLine("  TTFB:    ${it}ms") }
        }
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

    /** Wraps in single quotes; each embedded `'` becomes `'\''` (close, escaped quote, reopen). */
    private fun String.shellQuote(): String = "'" + replace("'", "'\\''") + "'"

    private fun List<NetHeader>.toHarHeaders(): List<Map<String, String>> =
        map { mapOf("name" to it.name, "value" to it.value) }

    /** Splits `a=1&b=2` into HAR `{name, value}` pairs; pairs without `=` are dropped. Internal for tests. */
    internal fun parseQueryString(query: String?): List<Map<String, String>> {
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
