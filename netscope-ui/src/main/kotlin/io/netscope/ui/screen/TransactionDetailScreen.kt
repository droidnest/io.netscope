package io.netscope.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.netscope.model.NetHeader
import io.netscope.model.NetTransaction
import io.netscope.ui.component.MethodChip
import io.netscope.ui.component.NetScopeAccent
import io.netscope.ui.component.NetScopeCard
import io.netscope.ui.component.NetScopeDarkBackground
import io.netscope.ui.component.NetScopeMuted
import io.netscope.ui.component.NetScopeSurface
import io.netscope.ui.component.StatusBadge
import io.netscope.ui.component.color
import io.netscope.ui.component.formatBytes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    viewModel: io.netscope.ui.viewmodel.NetScopeViewModel,
    onBack: () -> Unit,
) {
    val transaction by viewModel.selectedTransaction.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Request", "Response")

    transaction?.let { tx ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MethodChip(method = tx.method)
                            StatusBadge(code = tx.responseCode, statusClass = tx.statusClass)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = NetScopeSurface),
                )
            },
            containerColor = NetScopeDarkBackground,
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                // URL header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NetScopeSurface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(text = tx.host, color = NetScopeMuted, fontSize = 11.sp)
                    Text(
                        text = tx.path + (tx.query?.let { "?$it" } ?: ""),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                // Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = NetScopeSurface,
                    contentColor = NetScopeAccent,
                    edgePadding = 0.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = NetScopeAccent,
                            height = 2.dp,
                        )
                    },
                    divider = { Divider(color = Color(0xFF1E2D45)) },
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    color = if (selectedTab == index) NetScopeAccent else NetScopeMuted,
                                )
                            },
                        )
                    }
                }

                // Tab content
                when (selectedTab) {
                    0 -> OverviewTab(tx)
                    1 -> RequestTab(tx)
                    2 -> ResponseTab(tx)
                }
            }
        }
    }
}

// ── Tab: Overview ─────────────────────────────────────────────────────────────

@Composable
private fun OverviewTab(tx: NetTransaction) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionCard(title = "Timing") {
            InfoRow("Started", dateFormat.format(Date(tx.requestDate)))
            tx.responseDate?.let { InfoRow("Completed", dateFormat.format(Date(it))) }
            tx.durationMs?.let { InfoRow("Duration", "${it}ms") }
        }
        SectionCard(title = "Request") {
            InfoRow("Method", tx.method)
            InfoRow("URL", tx.url)
            InfoRow("Body size", tx.requestBodySize.formatBytes())
            tx.requestContentType?.let { InfoRow("Content-Type", it) }
        }
        SectionCard(title = "Response") {
            tx.responseCode?.let { InfoRow("Status", "$it ${tx.responseMessage ?: ""}") }
            InfoRow("Body size", tx.responseBodySize.formatBytes())
            tx.responseContentType?.let { InfoRow("Content-Type", it) }
        }
        tx.error?.let {
            SectionCard(title = "Error") {
                Text(text = it, color = Color(0xFFFF4D6A), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ── Tab: Request ──────────────────────────────────────────────────────────────

@Composable
private fun RequestTab(tx: NetTransaction) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (tx.requestHeaders.isNotEmpty()) {
            SectionCard(title = "Headers (${tx.requestHeaders.size})") {
                HeaderTable(tx.requestHeaders)
            }
        }
        tx.requestBody?.let { body ->
            SectionCard(title = "Body") {
                BodyView(body)
            }
        }
    }
}

// ── Tab: Response ─────────────────────────────────────────────────────────────

@Composable
private fun ResponseTab(tx: NetTransaction) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (tx.responseHeaders.isNotEmpty()) {
            SectionCard(title = "Headers (${tx.responseHeaders.size})") {
                HeaderTable(tx.responseHeaders)
            }
        }
        tx.responseBody?.let { body ->
            SectionCard(title = "Body") {
                BodyView(body)
            }
        }
    }
}

// ── Shared sub-components ─────────────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1A2235))
            .padding(16.dp),
    ) {
        Text(
            text = title,
            color = NetScopeAccent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = NetScopeMuted, fontSize = 12.sp, modifier = Modifier.weight(0.35f))
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(0.65f),
        )
    }
}

@Composable
private fun HeaderTable(headers: List<NetHeader>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        headers.forEach { header ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = header.name,
                    color = if (header.isRedacted) Color(0xFFFF4D6A) else NetScopeMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(0.4f),
                )
                Text(
                    text = header.value,
                    color = if (header.isRedacted) Color(0xFFFF4D6A).copy(alpha = 0.7f) else Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(0.6f),
                )
            }
        }
    }
}

@Composable
private fun BodyView(body: String) {
    val highlighted = remember(body) { body.highlightedBody() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0A0E1A))
            .horizontalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Text(
            text = highlighted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp,
        )
    }
}

// ── JSON syntax highlighting ──────────────────────────────────────────────────

private val JsonKeyColor     = Color(0xFF7DD3FC) // sky-300 — keys
private val JsonStringColor  = Color(0xFF86EFAC) // green-300 — string values
private val JsonNumberColor  = Color(0xFFFBBF24) // amber-400 — numbers
private val JsonKeywordColor = Color(0xFFA78BFA) // violet-400 — true / false / null
private val JsonPunctColor   = Color(0xFF6B7FA3) // muted — { } [ ] : ,
private val JsonDefaultColor = Color(0xFFCDD6F4) // plain text / fallback

/**
 * Returns an [AnnotatedString] with JSON syntax colouring, or a plain-coloured
 * [AnnotatedString] for non-JSON bodies.
 */
private fun String.highlightedBody(): AnnotatedString {
    val trimmed = trim()
    val isJson = trimmed.startsWith("{") || trimmed.startsWith("[")
    val pretty = if (isJson) trimmed.prettyPrintJson() else trimmed

    return if (!isJson) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = JsonDefaultColor)) { append(pretty) }
        }
    } else {
        buildAnnotatedString { appendJsonHighlighted(pretty) }
    }
}

/**
 * Appends [json] to the receiver [AnnotatedString.Builder] character-by-character,
 * applying colour spans per JSON token type.
 *
 * State machine tracks whether we're inside a string, and whether that string is
 * a key (followed by `:`) or a value.
 */
private fun AnnotatedString.Builder.appendJsonHighlighted(json: String) {
    var i = 0

    fun peekIsColon(): Boolean {
        var j = i
        while (j < json.length && (json[j] == ' ' || json[j] == '\n' || json[j] == '\r' || json[j] == '\t')) j++
        return j < json.length && json[j] == ':'
    }

    while (i < json.length) {
        val ch = json[i]
        when {
            // ── String token ──────────────────────────────────────────────
            ch == '"' -> {
                val start = i
                i++ // consume opening quote
                while (i < json.length) {
                    when {
                        json[i] == '\\' -> i += 2            // skip escaped char
                        json[i] == '"'  -> { i++; break }    // closing quote
                        else            -> i++
                    }
                }
                val raw = json.substring(start, i)
                // A string is a key if what follows (ignoring whitespace) is ':'
                val color = if (peekIsColon()) JsonKeyColor else JsonStringColor
                withStyle(SpanStyle(color = color)) { append(raw) }
            }

            // ── Number ────────────────────────────────────────────────────
            ch == '-' || ch.isDigit() -> {
                val start = i
                if (ch == '-') i++
                while (i < json.length && (json[i].isDigit() || json[i] == '.' || json[i] in "eE+-")) i++
                withStyle(SpanStyle(color = JsonNumberColor)) { append(json.substring(start, i)) }
            }

            // ── Keywords ──────────────────────────────────────────────────
            json.startsWith("true", i) -> {
                withStyle(SpanStyle(color = JsonKeywordColor)) { append("true") }; i += 4
            }
            json.startsWith("false", i) -> {
                withStyle(SpanStyle(color = JsonKeywordColor)) { append("false") }; i += 5
            }
            json.startsWith("null", i) -> {
                withStyle(SpanStyle(color = JsonKeywordColor)) { append("null") }; i += 4
            }

            // ── Punctuation ───────────────────────────────────────────────
            ch in "{}[],:." -> {
                withStyle(SpanStyle(color = JsonPunctColor)) { append(ch) }; i++
            }

            // ── Whitespace / everything else ──────────────────────────────
            else -> {
                withStyle(SpanStyle(color = JsonDefaultColor)) { append(ch) }; i++
            }
        }
    }
}

private fun String.prettyPrintJson(): String = try {
    val sb = StringBuilder()
    var indent = 0
    var inString = false
    var i = 0
    while (i < length) {
        val ch = this[i]
        when {
            ch == '"' && !inString -> { inString = true; sb.append(ch) }
            ch == '"' && inString  -> { inString = false; sb.append(ch) }
            ch == '\\' && inString -> { sb.append(ch); sb.append(this[++i]) }
            inString               -> sb.append(ch)
            ch == '{' || ch == '[' -> {
                sb.append(ch); sb.append('\n')
                indent++; sb.append("  ".repeat(indent))
            }
            ch == '}' || ch == ']' -> {
                sb.append('\n'); indent--
                sb.append("  ".repeat(indent)); sb.append(ch)
            }
            ch == ','              -> { sb.append(ch); sb.append('\n'); sb.append("  ".repeat(indent)) }
            ch == ':'              -> sb.append(": ")
            ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' -> { /* skip stale whitespace */ }
            else                   -> sb.append(ch)
        }
        i++
    }
    sb.toString()
} catch (_: Exception) { this }
