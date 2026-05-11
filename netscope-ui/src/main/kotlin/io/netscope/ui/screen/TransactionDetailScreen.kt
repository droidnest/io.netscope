package io.netscope.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import io.netscope.ui.component.ColorAmber
import io.netscope.ui.component.ColorError
import io.netscope.ui.component.ColorPink
import io.netscope.ui.component.ColorPurple
import io.netscope.ui.component.ColorSuccess
import io.netscope.ui.component.NetScopeCardAlt
import io.netscope.ui.component.NetScopeSurface
import io.netscope.ui.component.NetScopeText2
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
    val tabs = listOf("Overview", "Request", "Response", "Timing")
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
                                DropdownMenuItem(
                                    text = { Text("Share as HAR", color = NetScopeOnSurface, fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = NetScopeMuted, modifier = Modifier.size(16.dp))
                                    },
                                    onClick = {
                                        overflowExpanded = false
                                        NetScopeExporter.share(
                                            context = context,
                                            text = NetScopeExporter.toHar(listOf(tx)),
                                            chooserTitle = "Share as HAR",
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
                        0    -> OverviewTab(tx, context, onCopied)
                        1    -> RequestTab(tx, context, onCopied)
                        2    -> ResponseTab(tx, context, onCopied)
                        3    -> TimingTab(tx)
                        else -> Unit
                    }
                }
            }
        }
    }
}

// ── Tab: Overview ─────────────────────────────────────────────────────────────

@Composable
private fun OverviewTab(
    tx: NetTransaction,
    context: android.content.Context,
    onCopied: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // Request dcard
        SectionCard(title = "Request", modifier = Modifier.padding(horizontal = 12.dp)) {
            InfoRow("Method",    tx.method)
            InfoRow("URL",       tx.url, valueColor = NetScopeAccent, smallValue = true)
            InfoRow("Body size", tx.requestBodySize.formatBytes())
            tx.requestContentType?.let { InfoRow("Content-Type", it, smallValue = true) }
        }

        Spacer(Modifier.height(8.dp))

        // Response dcard
        SectionCard(title = "Response", modifier = Modifier.padding(horizontal = 12.dp)) {
            tx.responseCode?.let {
                InfoRow("Status", "$it ${tx.responseMessage ?: ""}",
                    valueColor = tx.statusClass.color())
            }
            InfoRow("Body size",  tx.responseBodySize.formatBytes())
            tx.responseContentType?.let { InfoRow("Content-Type", it, smallValue = true) }
        }

        Spacer(Modifier.height(8.dp))

        // Mini stat cards: Duration · Size · Protocol
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiniStatCard(
                value = tx.durationMs?.let { "${it}ms" } ?: "—",
                label = "Duration",
                valueColor = when {
                    tx.durationMs == null     -> NetScopeMuted
                    tx.durationMs!! <= 300L   -> ColorSuccess
                    tx.durationMs!! <= 1000L  -> ColorAmber
                    else                      -> ColorError
                },
                modifier = Modifier.weight(1f),
            )
            MiniStatCard(
                value      = tx.totalSize.formatBytes(),
                label      = "Size",
                valueColor = NetScopeAccent,
                modifier   = Modifier.weight(1f),
            )
            MiniStatCard(
                value      = if (tx.url.startsWith("https")) "HTTPS" else "HTTP",
                label      = "Protocol",
                valueColor = ColorPurple,
                modifier   = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Copy as cURL action row
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(NetScopeAccent.copy(alpha = 0.08f))
                .border(0.5.dp, NetScopeAccent.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                .clickable {
                    NetScopeExporter.copyToClipboard(
                        context = context,
                        text    = NetScopeExporter.toCurl(tx),
                        label   = "cURL command",
                    )
                    onCopied("cURL command")
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                modifier             = Modifier.fillMaxWidth(),
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text       = "Copy as cURL",
                    color      = NetScopeAccent,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    imageVector        = Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint               = NetScopeAccent,
                    modifier           = Modifier.size(14.dp),
                )
            }
        }

        // Error card
        tx.error?.let { err ->
            Spacer(Modifier.height(8.dp))
            SectionCard(title = "Error", modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(
                    text       = err,
                    color      = ColorError,
                    fontSize   = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp,
                    modifier   = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun MiniStatCard(
    value: String,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(NetScopeCard)
            .border(0.5.dp, NetScopeBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text       = value,
            color      = valueColor,
            fontSize   = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text       = label,
            color      = NetScopeMuted,
            fontSize   = 8.sp,
            fontFamily = FontFamily.SansSerif,
        )
    }
}

// ── Tab: Timing ───────────────────────────────────────────────────────────────

private data class PhaseSegment(val label: String, val durationMs: Long, val color: Color)

private val PhaseDns      = ColorPurple
private val PhaseConnect  = Color(0xFF00C8F0)  // Cyan
private val PhaseSsl      = ColorPink
private val PhaseTtfb     = ColorAmber
private val PhaseDownload = ColorSuccess

@Composable
private fun TimingTab(tx: NetTransaction) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val segments = remember(tx.dnsMs, tx.connectMs, tx.sslMs, tx.ttfbMs) {
        buildList {
            tx.dnsMs?.let     { add(PhaseSegment("DNS lookup",    it, PhaseDns))      }
            tx.connectMs?.let { add(PhaseSegment("TCP connect",   it, PhaseConnect))  }
            tx.sslMs?.let     { add(PhaseSegment("TLS handshake", it, PhaseSsl))      }
            tx.ttfbMs?.let    { add(PhaseSegment("TTFB",          it, PhaseTtfb))     }
        }
    }
    val total = remember(segments) { segments.sumOf { it.durationMs }.coerceAtLeast(1L) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Summary card ─────────────────────────────────────────────────────
        SectionCard(title = "Summary", modifier = Modifier.padding(horizontal = 12.dp)) {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = dateFormat.format(Date(tx.requestDate)),
                        color      = NetScopeText2,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(text = "Started", color = NetScopeMuted, fontSize = 8.sp,
                        fontFamily = FontFamily.SansSerif)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = tx.responseDate?.let { dateFormat.format(Date(it)) } ?: "—",
                        color      = NetScopeText2,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(text = "Completed", color = NetScopeMuted, fontSize = 8.sp,
                        fontFamily = FontFamily.SansSerif)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = tx.durationMs?.let { "${it}ms" } ?: "—",
                        color      = if (tx.durationMs != null) ColorSuccess else NetScopeMuted,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(text = "Total", color = NetScopeMuted, fontSize = 8.sp,
                        fontFamily = FontFamily.SansSerif)
                }
            }
        }

        // ── Network phases card ──────────────────────────────────────────────
        if (segments.isNotEmpty()) {
            SectionCard(title = "Network phases", modifier = Modifier.padding(horizontal = 12.dp)) {
                Column(
                    modifier              = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement   = Arrangement.spacedBy(7.dp),
                ) {
                    segments.forEach { seg ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Colored dot
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(seg.color),
                            )
                            // Phase name
                            Text(
                                text     = seg.label,
                                color    = NetScopeText2,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.width(90.dp),
                            )
                            // Track bar
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NetScopeCardAlt),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(seg.durationMs.toFloat() / total)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(seg.color),
                                )
                            }
                            // Ms value
                            Text(
                                text       = "${seg.durationMs}ms",
                                color      = NetScopeOnSurface,
                                fontSize   = 9.sp,
                                fontFamily = FontFamily.SansSerif,
                                modifier   = Modifier.width(36.dp),
                            )
                        }
                    }
                }
            }

            // ── Stacked timeline bar ──────────────────────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text     = "Timeline (${total}ms total)",
                    color    = NetScopeMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.SansSerif,
                )
                // Stacked bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp)),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    segments.forEachIndexed { i, seg ->
                        val shape = when {
                            segments.size == 1 -> RoundedCornerShape(7.dp)
                            i == 0             -> RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp)
                            i == segments.size - 1 -> RoundedCornerShape(topEnd = 7.dp, bottomEnd = 7.dp)
                            else               -> RoundedCornerShape(0.dp)
                        }
                        Box(
                            modifier = Modifier
                                .weight(seg.durationMs.toFloat() / total)
                                .fillMaxHeight()
                                .clip(shape)
                                .background(seg.color),
                        )
                    }
                }
                // Legend
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    segments.forEach { seg ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(seg.color),
                            )
                            Text(
                                text     = seg.label.substringBefore(" "),
                                color    = NetScopeMuted,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.SansSerif,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
    }
}

// ── Tab: Request ──────────────────────────────────────────────────────────────

@Composable
private fun RequestTab(tx: NetTransaction, context: android.content.Context, onCopied: (String) -> Unit) {
    HttpMessageTab(
        headers = tx.requestHeaders,
        body = tx.requestBody,
        emptyMessage = "No request data captured",
        headersLabel = "Request headers",
        bodyLabel = "Request body",
        context = context,
        onCopied = onCopied,
    )
}

// ── Tab: Response ─────────────────────────────────────────────────────────────

@Composable
private fun ResponseTab(tx: NetTransaction, context: android.content.Context, onCopied: (String) -> Unit) {
    HttpMessageTab(
        headers = tx.responseHeaders,
        body = tx.responseBody,
        emptyMessage = if (tx.statusClass == NetTransaction.StatusClass.PENDING) "Waiting for response…"
                       else "No response data captured",
        headersLabel = "Response headers",
        bodyLabel = "Response body",
        context = context,
        onCopied = onCopied,
    )
}

// ── Tab: HTTP message (shared by Request + Response) ─────────────────────────

@Composable
private fun HttpMessageTab(
    headers: List<NetHeader>,
    body: String?,
    emptyMessage: String,
    headersLabel: String,
    bodyLabel: String,
    context: android.content.Context,
    onCopied: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (headers.isNotEmpty()) {
            SectionCard(
                title    = "Headers (${headers.size})",
                onCopy   = {
                    NetScopeExporter.copyToClipboard(context, NetScopeExporter.headersText(headers), headersLabel)
                    onCopied(headersLabel)
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                HeaderTable(headers)
            }
        }
        body?.let { b ->
            SectionCard(
                title    = "Body",
                onCopy   = {
                    NetScopeExporter.copyToClipboard(context, b, bodyLabel)
                    onCopied(bodyLabel)
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                // empty header only — body rendered separately as json-block
            }
            BodyView(b, modifier = Modifier.padding(horizontal = 12.dp))
        }
        if (headers.isEmpty() && body == null) {
            EmptyTabState(emptyMessage)
        }
        Spacer(Modifier.height(10.dp))
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
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NetScopeCard)
            .border(0.5.dp, NetScopeBorder, RoundedCornerShape(10.dp)),
    ) {
        // dcard header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NetScopeCard)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text       = title,
                color      = NetScopeAccent,
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
            )
            if (onCopy != null) {
                IconButton(
                    onClick  = onCopy,
                    modifier = Modifier.size(20.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Default.ContentCopy,
                        contentDescription = "Copy $title",
                        tint               = NetScopeMuted,
                        modifier           = Modifier.size(13.dp),
                    )
                }
            }
        }
        androidx.compose.material3.HorizontalDivider(color = NetScopeBorder, thickness = 0.5.dp)
        // Content
        content()
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = NetScopeOnSurface,
    smallValue: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text       = label,
            color      = NetScopeText2,
            fontSize   = 10.sp,
            fontFamily = FontFamily.SansSerif,
            modifier   = Modifier.weight(0.35f),
        )
        Text(
            text       = value,
            color      = valueColor,
            fontSize   = if (smallValue) 9.sp else 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier   = Modifier.weight(0.65f),
        )
    }
    androidx.compose.material3.HorizontalDivider(
        color     = NetScopeBorder.copy(alpha = 0.6f),
        thickness = 0.5.dp,
        modifier  = Modifier.padding(horizontal = 0.dp),
    )
}

@Composable
private fun HeaderTable(headers: List<NetHeader>) {
    Column {
        headers.forEach { header ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text       = header.name,
                    color      = if (header.isRedacted) ColorError else NetScopeText2,
                    fontSize   = 10.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier   = Modifier.weight(0.4f),
                )
                Text(
                    text       = header.value,
                    color      = if (header.isRedacted) ColorError.copy(alpha = 0.7f) else NetScopeOnSurface,
                    fontSize   = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier   = Modifier.weight(0.6f),
                )
            }
            androidx.compose.material3.HorizontalDivider(
                color     = NetScopeBorder.copy(alpha = 0.6f),
                thickness = 0.5.dp,
            )
        }
    }
}

@Composable
private fun BodyView(body: String, modifier: Modifier = Modifier) {
    val highlighted = remember(body) { body.highlightedBody() }
    val scrollState = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(0.5.dp, NetScopeBorder, RoundedCornerShape(12.dp))
            .background(NetScopeCardAlt)
            .horizontalScroll(scrollState)
            .padding(12.dp),
    ) {
        Text(
            text       = highlighted,
            fontSize   = 10.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight  = 17.5.sp,
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
    return when {
        trimmed.startsWith("{") || trimmed.startsWith("[") ->
            buildAnnotatedString { appendJsonHighlighted(trimmed.prettyPrintJson()) }
        trimmed.startsWith("<") ->
            buildAnnotatedString { appendXmlHighlighted(trimmed) }
        else ->
            buildAnnotatedString { withStyle(SpanStyle(color = JsonDefaultColor)) { append(trimmed) } }
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

// ── XML syntax highlighting ───────────────────────────────────────────────────

private fun AnnotatedString.Builder.appendXmlHighlighted(xml: String) {
    var i = 0
    while (i < xml.length) {
        when {
            xml.startsWith("<!--", i) -> {
                val end = xml.indexOf("-->", i + 4).let { if (it == -1) xml.length else it + 3 }
                withStyle(SpanStyle(color = JsonPunctColor)) { append(xml.substring(i, end)) }
                i = end
            }
            xml.startsWith("<?", i) -> {
                val end = xml.indexOf("?>", i + 2).let { if (it == -1) xml.length else it + 2 }
                withStyle(SpanStyle(color = JsonKeywordColor)) { append(xml.substring(i, end)) }
                i = end
            }
            xml[i] == '<' -> {
                withStyle(SpanStyle(color = JsonPunctColor)) { append('<') }
                i++
                if (i < xml.length && xml[i] == '/') {
                    withStyle(SpanStyle(color = JsonPunctColor)) { append('/') }
                    i++
                }
                val nameStart = i
                while (i < xml.length && xml[i] !in " \t\n\r>/") i++
                withStyle(SpanStyle(color = JsonKeyColor)) { append(xml.substring(nameStart, i)) }
                while (i < xml.length && xml[i] != '>' && !(xml[i] == '/' && i + 1 < xml.length && xml[i + 1] == '>')) {
                    if (xml[i].isWhitespace()) { append(xml[i++]) }
                    else {
                        val attrStart = i
                        while (i < xml.length && xml[i] !in "= \t\n\r>/") i++
                        withStyle(SpanStyle(color = JsonStringColor)) { append(xml.substring(attrStart, i)) }
                        if (i < xml.length && xml[i] == '=') {
                            withStyle(SpanStyle(color = JsonPunctColor)) { append('=') }
                            i++
                            if (i < xml.length && xml[i] == '"') {
                                val q = i++
                                while (i < xml.length && xml[i] != '"') i++
                                if (i < xml.length) i++
                                withStyle(SpanStyle(color = JsonNumberColor)) { append(xml.substring(q, i)) }
                            }
                        }
                    }
                }
                if (i < xml.length && xml[i] == '/') { withStyle(SpanStyle(color = JsonPunctColor)) { append('/') }; i++ }
                if (i < xml.length && xml[i] == '>') { withStyle(SpanStyle(color = JsonPunctColor)) { append('>') }; i++ }
            }
            else -> {
                val start = i
                while (i < xml.length && xml[i] != '<') i++
                withStyle(SpanStyle(color = JsonDefaultColor)) { append(xml.substring(start, i)) }
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
