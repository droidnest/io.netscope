package io.netscope.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.gson.GsonBuilder
import io.netscope.model.NetHeader
import io.netscope.model.NetTransaction
import java.io.File
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
 * - [share]            — share text via the Android share sheet, as a file when it is large
 */
object NetScopeExporter {

    /** Most characters sent through Binder: a UTF-16 parcel of 200 KB, well under its 1 MB buffer. */
    const val MAX_TRANSFER_CHARS = 100_000

    // Same directory as res/xml/netscope_export_paths.xml, same suffix as the manifest's provider
    private const val SHARE_DIRECTORY = "netscope-share"
    private const val AUTHORITY_SUFFIX = ".netscope.exports"

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
        append(" ${transaction.url.shellQuoted()}")

        transaction.requestHeaders.forEach { header ->
            append(" \\\n  -H ${"${header.name}: ${header.value}".shellQuoted()}")
        }

        transaction.requestBody?.let { body ->
            append(" \\\n  --data-raw ${body.shellQuoted()}")
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
     * Copies [text] to the system clipboard with the given [label]. The clipboard goes through
     * Binder, which fails on large data, so text over [MAX_TRANSFER_CHARS] is cut with a note.
     */
    fun copyToClipboard(context: Context, text: String, label: String = "NetScope") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text.forClipboard()))
    }

    /**
     * Opens the Android share sheet to share [text] with other apps. Text over
     * [MAX_TRANSFER_CHARS] is too large for an Intent, so it is shared as a file instead.
     *
     * @param chooserTitle Optional title shown on the share chooser dialog.
     * @param fileName     Name of the file when [text] is shared as one.
     */
    fun share(
        context: Context,
        text: String,
        chooserTitle: String = "Share via",
        fileName: String = "netscope.txt",
    ) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            if (text.length <= MAX_TRANSFER_CHARS) {
                putExtra(Intent.EXTRA_TEXT, text)
            } else {
                val uri = writeShareFile(context = context, text = text, fileName = fileName)
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Wraps this value for a POSIX shell: nothing inside single quotes is interpreted. */
    private fun String.shellQuoted(): String = "'" + replace("'", "'\\''") + "'"

    internal fun String.forClipboard(): String {
        if (length <= MAX_TRANSFER_CHARS) return this
        val end = if (this[MAX_TRANSFER_CHARS - 1].isHighSurrogate()) MAX_TRANSFER_CHARS - 1 else MAX_TRANSFER_CHARS
        return substring(0, end) + "\n… [cut by NetScope: ${length - end} more characters]"
    }

    private fun writeShareFile(context: Context, text: String, fileName: String): Uri {
        val directory = File(context.cacheDir, SHARE_DIRECTORY).apply { mkdirs() }
        val file = File(directory, fileName).apply { writeText(text) }
        return FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
    }

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
