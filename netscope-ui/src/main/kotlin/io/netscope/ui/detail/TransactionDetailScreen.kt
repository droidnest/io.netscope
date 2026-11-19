package io.netscope.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.netscope.export.NetScopeExporter
import io.netscope.model.NetHeader
import io.netscope.model.NetTransaction
import io.netscope.ui.component.MethodChip
import io.netscope.ui.component.NetScopeAccent
import io.netscope.ui.component.NetScopeBorder
import io.netscope.ui.component.NetScopeCard
import io.netscope.ui.component.NetScopeDarkBackground
import io.netscope.ui.component.NetScopeDropdown
import io.netscope.ui.component.NetScopeMuted
import io.netscope.ui.component.NetScopeOnSurface
import io.netscope.ui.component.ColorAmber
import io.netscope.ui.component.ColorCyan
import io.netscope.ui.component.ColorError
import io.netscope.ui.component.ColorPink
import io.netscope.ui.component.ColorPurple
import io.netscope.ui.component.ColorSuccess
import io.netscope.ui.component.NetScopeCardAlt
import io.netscope.ui.component.NetScopeCode
import io.netscope.ui.component.NetScopeSurface
import io.netscope.ui.component.NetScopeSyntax
import io.netscope.ui.component.NetScopeText2
import io.netscope.ui.component.StatusBadge
import io.netscope.ui.component.color
import io.netscope.ui.component.formatBytes
import io.netscope.ui.component.scrollIndicator
import io.netscope.ui.mvi.UiIntent
import io.netscope.ui.mvi.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SEARCHABLE_TABS = 1..2

/** How long typing pauses before a search runs: each run scans the whole body. */
private const val SEARCH_DEBOUNCE_MILLIS = 200L

/** Body lines kept above the line of the current match when it is scrolled to. */
private const val SEARCH_CONTEXT_LINES = 3

/** The search the Request and Response tabs share. */
private data class MessageSearch(val isOpen: Boolean, val query: String)

private val TAB_SLIDE = spring(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionDetailScreen(
    uiState: UiState<TransactionDetailUiState>,
    onIntent: (UiIntent) -> Unit,
    onBack: () -> Unit,
) {
    val detail = uiState.dataOrNull()
    val selectedTab = detail?.selectedTab ?: 0
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

    if (uiState is UiState.Error) {
        AlertDialog(
            onDismissRequest = {
                onIntent(TransactionDetailUiIntent.ErrorShown)
                onBack()
            },
            containerColor = NetScopeCard,
            text = { Text(text = uiState.message.orEmpty(), color = NetScopeOnSurface) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onIntent(TransactionDetailUiIntent.ErrorShown)
                        onBack()
                    },
                ) {
                    Text(text = "OK", color = NetScopeAccent)
                }
            },
        )
    }

    detail?.let { state ->
        val tx = state.transaction
        val search = MessageSearch(isOpen = state.isSearchOpen, query = state.searchQuery)
        BackHandler(enabled = state.isSearchOpen) { onIntent(TransactionDetailUiIntent.SearchClosed) }
        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = NetScopeDropdown,
                        contentColor = NetScopeOnSurface,
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
                        // Only the Request and Response tabs hold text worth searching
                        if (selectedTab in SEARCHABLE_TABS && !state.isSearchOpen) {
                            IconButton(onClick = { onIntent(TransactionDetailUiIntent.SearchClicked) }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search headers and body",
                                    tint = NetScopeMuted,
                                )
                            }
                        }

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
                            IconButton(onClick = { onIntent(TransactionDetailUiIntent.MenuClicked) }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = NetScopeMuted,
                                )
                            }
                            DropdownMenu(
                                expanded = state.isMenuOpen,
                                onDismissRequest = { onIntent(TransactionDetailUiIntent.MenuDismissed) },
                                containerColor = NetScopeDropdown,
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Copy full summary", color = NetScopeOnSurface, fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = NetScopeMuted, modifier = Modifier.size(16.dp))
                                    },
                                    onClick = {
                                        onIntent(TransactionDetailUiIntent.MenuDismissed)
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
                                        onIntent(TransactionDetailUiIntent.MenuDismissed)
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
                                        onIntent(TransactionDetailUiIntent.MenuDismissed)
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
                                        onIntent(TransactionDetailUiIntent.MenuDismissed)
                                        NetScopeExporter.share(
                                            context = context,
                                            text = NetScopeExporter.toHar(listOf(tx)),
                                            chooserTitle = "Share as HAR",
                                            fileName = "netscope.har",
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
                            onClick = { onIntent(TransactionDetailUiIntent.TabClicked(tab = index)) },
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
                        // A spring carries on from where the tabs are when another one is tapped mid-way
                        (slideInHorizontally(TAB_SLIDE) { w -> direction * w } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(TAB_SLIDE) { w -> -direction * w } + fadeOut(tween(150)))
                    },
                    label = "tabContent",
                ) { tab ->
                    when (tab) {
                        0    -> OverviewTab(tx, context, onCopied)
                        1    -> RequestTab(tx, context, onCopied, search, onIntent)
                        2    -> ResponseTab(tx, context, onCopied, search, onIntent)
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
            fontSize   = 10.sp,
            fontFamily = FontFamily.SansSerif,
        )
    }
}

// ── Tab: Timing ───────────────────────────────────────────────────────────────

private data class PhaseSegment(val label: String, val durationMs: Long, val color: Color)

@Composable
private fun TimingTab(tx: NetTransaction) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val dnsColor = ColorPurple
    val connectColor = ColorCyan
    val sslColor = ColorPink
    val ttfbColor = ColorAmber
    val segments = remember(tx.dnsMs, tx.connectMs, tx.sslMs, tx.ttfbMs, dnsColor) {
        buildList {
            tx.dnsMs?.let     { add(PhaseSegment("DNS lookup",    it, dnsColor))     }
            tx.connectMs?.let { add(PhaseSegment("TCP connect",   it, connectColor)) }
            tx.sslMs?.let     { add(PhaseSegment("TLS handshake", it, sslColor))     }
            tx.ttfbMs?.let    { add(PhaseSegment("TTFB",          it, ttfbColor))    }
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
                    Text(text = "Started", color = NetScopeMuted, fontSize = 10.sp,
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
                    Text(text = "Completed", color = NetScopeMuted, fontSize = 10.sp,
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
                    Text(text = "Total", color = NetScopeMuted, fontSize = 10.sp,
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
                                fontSize = 10.sp,
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
                                fontSize   = 10.sp,
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
                    fontSize = 10.sp,
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
                                fontSize = 10.sp,
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
private fun RequestTab(
    tx: NetTransaction,
    context: android.content.Context,
    onCopied: (String) -> Unit,
    search: MessageSearch,
    onIntent: (UiIntent) -> Unit,
) {
    HttpMessageTab(
        headers = tx.requestHeaders,
        body = tx.requestBody,
        emptyMessage = "No request data captured",
        headersLabel = "Request headers",
        bodyLabel = "Request body",
        context = context,
        onCopied = onCopied,
        search = search,
        onIntent = onIntent,
    )
}

// ── Tab: Response ─────────────────────────────────────────────────────────────

@Composable
private fun ResponseTab(
    tx: NetTransaction,
    context: android.content.Context,
    onCopied: (String) -> Unit,
    search: MessageSearch,
    onIntent: (UiIntent) -> Unit,
) {
    HttpMessageTab(
        headers = tx.responseHeaders,
        body = tx.responseBody,
        emptyMessage = if (tx.statusClass == NetTransaction.StatusClass.PENDING) "Waiting for response…"
                       else "No response data captured",
        headersLabel = "Response headers",
        bodyLabel = "Response body",
        context = context,
        onCopied = onCopied,
        search = search,
        onIntent = onIntent,
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
    search: MessageSearch,
    onIntent: (UiIntent) -> Unit,
) {
    val listState = rememberLazyListState()
    val formattedBody by produceState<FormattedBody?>(initialValue = null, key1 = body) {
        value = body?.let { withContext(Dispatchers.Default) { formatBody(body = it) } }
    }

    // Marks follow the typed query; the counter and the arrows follow the matches once they are found
    val query = search.query
    val matches by produceState(initialValue = emptyList<SearchMatch>(), headers, formattedBody, query) {
        if (query.isEmpty()) {
            value = emptyList()
            return@produceState
        }
        delay(SEARCH_DEBOUNCE_MILLIS)
        val lines = formattedBody?.lines.orEmpty()
        value = withContext(Dispatchers.Default) { findMatches(headers = headers, lines = lines, query = query) }
    }
    var currentIndex by remember(matches) { mutableIntStateOf(0) }
    val current = matches.getOrNull(currentIndex)
    val bodyFirstItem = if (headers.isNotEmpty()) 2 else 1 // the headers card, then the body title

    LaunchedEffect(current) {
        current?.let { listState.scrollToMatch(match = it, bodyFirstItem = bodyFirstItem) }
    }

    val searchColors = SearchColors(
        match = ColorAmber.copy(alpha = 0.35f),
        current = ColorAmber,
        onCurrent = Color.Black,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        if (search.isOpen) {
            MessageSearchBar(
                query = query,
                matchCount = matches.size,
                currentIndex = currentIndex,
                onQueryChange = { onIntent(TransactionDetailUiIntent.SearchQueryTyped(query = it)) },
                onPrevious = { if (matches.isNotEmpty()) currentIndex = (currentIndex - 1).mod(matches.size) },
                onNext = { if (matches.isNotEmpty()) currentIndex = (currentIndex + 1).mod(matches.size) },
                onClose = { onIntent(TransactionDetailUiIntent.SearchClosed) },
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .scrollIndicator(state = listState),
            contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp),
        ) {
            if (headers.isNotEmpty()) {
                item(key = "headers", contentType = "headers") {
                    SectionCard(
                        title    = "Headers (${headers.size})",
                        onCopy   = {
                            NetScopeExporter.copyToClipboard(context, NetScopeExporter.headersText(headers), headersLabel)
                            onCopied(headersLabel)
                        },
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    ) {
                        HeaderTable(
                            headers = headers,
                            query = query,
                            current = current as? SearchMatch.InHeader,
                            searchColors = searchColors,
                        )
                    }
                }
            }
            body?.let { b ->
                item(key = "bodyTitle", contentType = "bodyTitle") {
                    BodyTitle(
                        onCopy = {
                            NetScopeExporter.copyToClipboard(context, b, bodyLabel)
                            onCopied(bodyLabel)
                        },
                    )
                }
                val formatted = formattedBody
                if (formatted == null) {
                    item(key = "bodyFormatting", contentType = "bodyFormatting") { EmptyTabState("Formatting body…") }
                } else {
                    val currentInBody = current as? SearchMatch.InBody
                    items(count = formatted.lines.size, contentType = { "bodyLine" }) { index ->
                        BodyLine(
                            line = formatted.lines[index],
                            kind = formatted.kind,
                            isLast = index == formatted.lines.lastIndex,
                            query = query,
                            currentStart = currentInBody?.takeIf { it.line == index }?.start,
                            searchColors = searchColors,
                        )
                    }
                }
            }
            if (headers.isEmpty() && body == null) {
                item(key = "empty", contentType = "empty") { EmptyTabState(emptyMessage) }
            }
        }
    }
}

/** Brings [match] into view when it is not, a few lines below the top so that what leads to it shows too. */
private suspend fun LazyListState.scrollToMatch(match: SearchMatch, bodyFirstItem: Int) {
    val (target, context) = when (match) {
        is SearchMatch.InHeader -> 0 to 0
        is SearchMatch.InBody   -> bodyFirstItem + match.line to SEARCH_CONTEXT_LINES
    }
    val isFullyVisible = layoutInfo.visibleItemsInfo.any { item ->
        item.index == target &&
            item.offset >= layoutInfo.viewportStartOffset &&
            item.offset + item.size <= layoutInfo.viewportEndOffset
    }
    if (!isFullyVisible) scrollToItem(index = (target - context).coerceAtLeast(0))
}

@Composable
private fun MessageSearchBar(
    query: String,
    matchCount: Int,
    currentIndex: Int,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NetScopeSurface)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NetScopeCard)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = NetScopeMuted,
                modifier = Modifier.size(16.dp),
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = "Find in headers and body…",
                        color = NetScopeMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = TextStyle(
                        color = NetScopeOnSurface,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    cursorBrush = SolidColor(NetScopeAccent),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
            if (query.isNotEmpty()) {
                Text(
                    text = when {
                        matchCount == 0                  -> "0/0"
                        matchCount >= MAX_SEARCH_MATCHES -> "${currentIndex + 1}/$MAX_SEARCH_MATCHES+"
                        else                             -> "${currentIndex + 1}/$matchCount"
                    },
                    color = if (matchCount == 0) ColorError else NetScopeMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        IconButton(onClick = onPrevious, enabled = matchCount > 0, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Previous match",
                tint = if (matchCount > 0) NetScopeOnSurface else NetScopeMuted,
            )
        }
        IconButton(onClick = onNext, enabled = matchCount > 0, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Next match",
                tint = if (matchCount > 0) NetScopeOnSurface else NetScopeMuted,
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close search",
                tint = NetScopeMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
    HorizontalDivider(color = NetScopeBorder, thickness = 0.5.dp)
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
                CopyButton(description = "Copy $title", onClick = onCopy)
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
private fun HeaderTable(
    headers: List<NetHeader>,
    query: String,
    current: SearchMatch.InHeader?,
    searchColors: SearchColors,
) {
    Column {
        headers.forEachIndexed { index, header ->
            val currentHere = current?.takeIf { it.header == index }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text       = AnnotatedString(header.name).withMatches(
                        query = query,
                        currentStart = currentHere?.takeIf { !it.inValue }?.start,
                        colors = searchColors,
                    ),
                    color      = if (header.isRedacted) ColorError else NetScopeText2,
                    fontSize   = 10.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier   = Modifier.weight(0.4f),
                )
                Text(
                    text       = AnnotatedString(header.value).withMatches(
                        query = query,
                        currentStart = currentHere?.takeIf { it.inValue }?.start,
                        colors = searchColors,
                    ),
                    color      = if (header.isRedacted) ColorError else NetScopeOnSurface,
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

private val BodyEdgeRadius = 12.dp

/** The head of the body block, which its lines continue in the same surface. */
@Composable
private fun BodyTitle(onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .background(
                color = NetScopeCode,
                shape = RoundedCornerShape(topStart = BodyEdgeRadius, topEnd = BodyEdgeRadius),
            )
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text       = "Body",
            color      = NetScopeAccent,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        CopyButton(description = "Copy Body", onClick = onCopy)
    }
}

/** One line of a body. Lines wrap: a shared horizontal scroll cannot span the items of a lazy list. */
@Composable
private fun BodyLine(
    line: String,
    kind: BodyKind,
    isLast: Boolean,
    query: String,
    currentStart: Int?,
    searchColors: SearchColors,
) {
    val syntax = NetScopeSyntax
    val highlighted = remember(line, kind, syntax) { line.highlighted(kind = kind, colors = syntax) }
    val text = remember(highlighted, query, currentStart, searchColors) {
        highlighted.withMatches(query = query, currentStart = currentStart, colors = searchColors)
    }
    Text(
        text       = text,
        fontSize   = 11.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 18.sp,
        modifier   = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .background(
                color = NetScopeCode,
                shape = RoundedCornerShape(
                    bottomStart = if (isLast) BodyEdgeRadius else 0.dp,
                    bottomEnd = if (isLast) BodyEdgeRadius else 0.dp,
                ),
            )
            .padding(start = 12.dp, end = 12.dp, bottom = if (isLast) 12.dp else 0.dp),
    )
}

@Composable
private fun CopyButton(description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(
            imageVector        = Icons.Default.ContentCopy,
            contentDescription = description,
            tint               = NetScopeMuted,
            modifier           = Modifier.size(15.dp),
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
