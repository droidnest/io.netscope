package io.netscope.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.netscope.export.NetScopeExporter
import io.netscope.model.NetHeader
import io.netscope.model.NetTransaction
import io.netscope.ui.component.MethodChip
import io.netscope.ui.component.NetScopeAccent
import io.netscope.ui.component.NetScopeBorder
import io.netscope.ui.component.NetScopeCard
import io.netscope.ui.component.NetScopeCode
import io.netscope.ui.component.NetScopeDarkBackground
import io.netscope.ui.component.NetScopeDropdown
import io.netscope.ui.component.NetScopeMuted
import io.netscope.ui.component.NetScopeOnSurface
import io.netscope.ui.component.NetScopeSurface
import io.netscope.ui.component.StatusBadge
import io.netscope.ui.component.color
import io.netscope.ui.component.formatBytes
import kotlinx.coroutines.launch
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
    var previousTab by remember { mutableIntStateOf(0) }
    var overflowExpanded by remember { mutableStateOf(false) }
    val tabs = listOf("Overview", "Request", "Response")
    val context = LocalContext.current

    // ── Snackbar ──────────────────────────────────────────────────────────────
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    val onCopied: (String) -> Unit = { label ->
        snackbarScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = "$label copied",
                duration = SnackbarDuration.Short,
            )
        }
    }

    transaction?.let { tx ->
        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = NetScopeDropdown,
                        contentColor = Color.White,
                        actionColor = NetScopeAccent,
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            },
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
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = NetScopeOnSurface,
                            )
                        }
                    },
                    actions = {
                        // Primary one-tap action: copy as cURL
                        IconButton(onClick = {
                            NetScopeExporter.copyToClipboard(
                                context = context,
                                text = NetScopeExporter.toCurl(tx),
                                label = "cURL command",
                            )
                            onCopied("cURL command")
                        }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy as cURL",
                                tint = NetScopeMuted,
                            )
                        }

                        // Overflow: full summary copy/share + share as cURL
                        Box {
                            IconButton(onClick = { overflowExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = NetScopeMuted,
                                )
                            }
                            DropdownMenu(
                                expanded = overflowExpanded,
                                onDismissRequest = { overflowExpanded = false },
                                containerColor = NetScopeDropdown,
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Copy full summary", color = NetScopeOnSurface, fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = NetScopeMuted, modifier = Modifier.size(16.dp))
                                    },
                                    onClick = {
                                        overflowExpanded = false
                                        NetScopeExporter.copyToClipboard(
                                            context = context,
                                            text = NetScopeExporter.toFullSummary(tx),
                                            label = "Transaction summary",
                                        )
                                        onCopied("Full summary")
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Share as cURL", color = NetScopeOnSurface, fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = NetScopeMuted, modifier = Modifier.size(16.dp))
                                    },
                                    onClick = {
                                        overflowExpanded = false
                                        NetScopeExporter.share(
                                            context = context,
                                            text = NetScopeExporter.toCurl(tx),
                                            chooserTitle = "Share request as cURL",
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Share full summary", color = NetScopeOnSurface, fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = NetScopeMuted, modifier = Modifier.size(16.dp))
                                    },
                                    onClick = {
                                        overflowExpanded = false
                                        NetScopeExporter.share(
                                            context = context,
                                            text = NetScopeExporter.toFullSummary(tx),
                                            chooserTitle = "Share transaction",
                                        )
                                    },
                                )
                            }
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
                // ── URL strip with endpoint copy ──────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NetScopeSurface)
                        .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = tx.host, color = NetScopeMuted, fontSize = 11.sp)
                        Text(
                            text = tx.path + (tx.query?.let { "?$it" } ?: ""),
                            color = NetScopeOnSurface,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    IconButton(
                        onClick = {
                            NetScopeExporter.copyToClipboard(
                                context = context,
                                text = "${tx.method} ${tx.url}",
                                label = "Endpoint",
                            )
                            onCopied("Endpoint")
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy endpoint",
                            tint = NetScopeMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                // ── Tab bar ───────────────────────────────────────────────────
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = NetScopeSurface,
                    contentColor = NetScopeAccent,
                    edgePadding = 0.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = NetScopeAccent,
                            height = 2.dp,
                        )
                    },
                    divider = { HorizontalDivider(color = NetScopeBorder) },
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = {
                                previousTab = selectedTab
                                selectedTab = index
                            },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selectedTab == index) NetScopeAccent else NetScopeMuted,
                                )
                            },
                        )
                    }
                }

                // ── Tab content with directional slide ────────────────────────
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(250)) { w -> direction * w } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(200)) { w -> -direction * w } + fadeOut(tween(150)))
                    },
                    label = "tabContent",
                ) { tab ->
                    when (tab) {
                        0    -> OverviewTab(tx)
                        1    -> RequestTab(tx, context, onCopied)
                        2    -> ResponseTab(tx, context, onCopied)
                        else -> Unit
                    }
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

        // Network phase timeline (only when EventListener data is present)
        val hasPhaseData = tx.dnsMs != null || tx.connectMs != null ||
                tx.sslMs != null || tx.ttfbMs != null
        if (hasPhaseData) {
            SectionCard(title = "Network Phases") {
                TimingTimeline(tx)
            }
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
                Text(
                    text = it,
                    color = Color(0xFFFF4D6A),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

// ── Network phase timeline ────────────────────────────────────────────────────

private data class PhaseSegment(
    val label: String,
    val durationMs: Long,
    val color: Color,
)

private val PhaseDns     = Color(0xFF7DD3FC) // sky-300
private val PhaseConnect = Color(0xFF86EFAC) // green-300
private val PhaseSsl     = Color(0xFFA78BFA) // violet-400
private val PhaseTtfb    = Color(0xFFFBBF24) // amber-400

@Composable
private fun TimingTimeline(tx: NetTransaction) {
    val segments = remember(tx.dnsMs, tx.connectMs, tx.sslMs, tx.ttfbMs) {
        buildList {
            tx.dnsMs?.let     { add(PhaseSegment("DNS",     it, PhaseDns))     }
            tx.connectMs?.let { add(PhaseSegment("Connect", it, PhaseConnect)) }
            tx.sslMs?.let     { add(PhaseSegment("TLS",     it, PhaseSsl))     }
            tx.ttfbMs?.let    { add(PhaseSegment("TTFB",    it, PhaseTtfb))    }
        }
    }
    if (segments.isEmpty()) return

    val total = remember(segments) { segments.sumOf { it.durationMs }.coerceAtLeast(1L) }

    // Proportional bar
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp)),
    ) {
        segments.forEach { seg ->
            Box(
                modifier = Modifier
                    .weight(seg.durationMs.toFloat() / total)
                    .fillMaxSize()
                    .background(seg.color),
            )
        }
    }

    Spacer(Modifier.height(10.dp))

    // Legend
    segments.forEach { seg ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(seg.color),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = seg.label,
                color = NetScopeMuted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${seg.durationMs}ms",
                color = NetScopeOnSurface,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

// ── Tab: Request ──────────────────────────────────────────────────────────────

@Composable
private fun RequestTab(
    tx: NetTransaction,
    context: android.content.Context,
    onCopied: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (tx.requestHeaders.isNotEmpty()) {
            SectionCard(
                title = "Headers (${tx.requestHeaders.size})",
                onCopy = {
                    NetScopeExporter.copyToClipboard(
                        context = context,
                        text = NetScopeExporter.headersText(tx.requestHeaders),
                        label = "Request headers",
                    )
                    onCopied("Request headers")
                },
            ) {
                HeaderTable(tx.requestHeaders)
            }
        }
        tx.requestBody?.let { body ->
            SectionCard(
                title = "Body",
                onCopy = {
                    NetScopeExporter.copyToClipboard(
                        context = context,
                        text = body,
                        label = "Request body",
                    )
                    onCopied("Request body")
                },
            ) {
                BodyView(body)
            }
        }
        if (tx.requestHeaders.isEmpty() && tx.requestBody == null) {
            EmptyTabState("No request data captured")
        }
    }
}

// ── Tab: Response ─────────────────────────────────────────────────────────────

@Composable
private fun ResponseTab(
    tx: NetTransaction,
    context: android.content.Context,
    onCopied: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (tx.responseHeaders.isNotEmpty()) {
            SectionCard(
                title = "Headers (${tx.responseHeaders.size})",
                onCopy = {
                    NetScopeExporter.copyToClipboard(
                        context = context,
                        text = NetScopeExporter.headersText(tx.responseHeaders),
                        label = "Response headers",
                    )
                    onCopied("Response headers")
                },
            ) {
                HeaderTable(tx.responseHeaders)
            }
        }
        tx.responseBody?.let { body ->
            SectionCard(
                title = "Body",
                onCopy = {
                    NetScopeExporter.copyToClipboard(
                        context = context,
                        text = body,
                        label = "Response body",
                    )
                    onCopied("Response body")
                },
            ) {
                BodyView(body)
            }
        }
        if (tx.responseHeaders.isEmpty() && tx.responseBody == null) {
            EmptyTabState(
                if (tx.statusClass == NetTransaction.StatusClass.PENDING) "Waiting for response…"
                else "No response data captured"
            )
        }
    }
}

// ── Shared sub-components ─────────────────────────────────────────────────────

/**
 * Card with a labelled header and optional inline copy button.
 *
 * @param onCopy When non-null, a small copy icon is shown in the header row.
 *               Callers control what text gets placed on the clipboard.
 */
@Composable
private fun SectionCard(
    title: String,
    onCopy: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1A2235))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Title row with optional copy button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = NetScopeAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
            )
            if (onCopy != null) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(20.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy $title",
                        tint = NetScopeMuted,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
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
        Text(
            text = label,
            color = NetScopeMuted,
            fontSize = 12.sp,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            text = value,
            color = NetScopeOnSurface,
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
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NetScopeCode)
            .horizontalScroll(scrollState)
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

@Composable
private fun EmptyTabState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = NetScopeMuted,
            fontSize = 13.sp,
        )
    }
}

// ── JSON syntax highlighting ──────────────────────────────────────────────────

private val JsonKeyColor     = Color(0xFF7DD3FC)
private val JsonStringColor  = Color(0xFF86EFAC)
private val JsonNumberColor  = Color(0xFFFBBF24)
private val JsonKeywordColor = Color(0xFFA78BFA)
private val JsonPunctColor   = Color(0xFF6B7FA3)
private val JsonDefaultColor = Color(0xFFCDD6F4)

private fun String.highlightedBody(): AnnotatedString {
    val trimmed = trim()
    val isJson = trimmed.startsWith("{") || trimmed.startsWith("[")
    val pretty = if (isJson) trimmed.prettyPrintJson() else trimmed
    return if (!isJson) {
        buildAnnotatedString { withStyle(SpanStyle(color = JsonDefaultColor)) { append(pretty) } }
    } else {
        buildAnnotatedString { appendJsonHighlighted(pretty) }
    }
}

private fun AnnotatedString.Builder.appendJsonHighlighted(json: String) {
    var i = 0

    fun peekIsColon(): Boolean {
        var j = i
        while (j < json.length && json[j] in " \n\r\t") j++
        return j < json.length && json[j] == ':'
    }

    while (i < json.length) {
        val ch = json[i]
        when {
            ch == '"' -> {
                val start = i
                i++
                while (i < json.length) {
                    when {
                        json[i] == '\\' -> i += 2
                        json[i] == '"'  -> { i++; break }
                        else            -> i++
                    }
                }
                val color = if (peekIsColon()) JsonKeyColor else JsonStringColor
                withStyle(SpanStyle(color = color)) { append(json.substring(start, i)) }
            }
            ch == '-' || ch.isDigit() -> {
                val start = i
                if (ch == '-') i++
                while (i < json.length && (json[i].isDigit() || json[i] == '.' || json[i] in "eE+-")) i++
                withStyle(SpanStyle(color = JsonNumberColor)) { append(json.substring(start, i)) }
            }
            json.startsWith("true",  i) -> { withStyle(SpanStyle(color = JsonKeywordColor)) { append("true")  }; i += 4 }
            json.startsWith("false", i) -> { withStyle(SpanStyle(color = JsonKeywordColor)) { append("false") }; i += 5 }
            json.startsWith("null",  i) -> { withStyle(SpanStyle(color = JsonKeywordColor)) { append("null")  }; i += 4 }
            ch in "{}[],:." -> { withStyle(SpanStyle(color = JsonPunctColor)) { append(ch) }; i++ }
            else -> { withStyle(SpanStyle(color = JsonDefaultColor)) { append(ch) }; i++ }
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
            ch == '"' && !inString -> { inString = true;  sb.append(ch) }
            ch == '"' && inString  -> { inString = false; sb.append(ch) }
            ch == '\\' && inString -> { sb.append(ch); sb.append(this[++i]) }
            inString               -> sb.append(ch)
            ch == '{' || ch == '[' -> { sb.append(ch); sb.append('\n'); indent++; sb.append("  ".repeat(indent)) }
            ch == '}' || ch == ']' -> { sb.append('\n'); indent--; sb.append("  ".repeat(indent)); sb.append(ch) }
            ch == ','              -> { sb.append(ch); sb.append('\n'); sb.append("  ".repeat(indent)) }
            ch == ':'              -> sb.append(": ")
            ch in " \n\r\t"       -> { /* skip pre-existing whitespace */ }
            else                   -> sb.append(ch)
        }
        i++
    }
    sb.toString()
} catch (_: Exception) { this }
