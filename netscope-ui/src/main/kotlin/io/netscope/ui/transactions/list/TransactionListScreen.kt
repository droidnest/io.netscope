package io.netscope.ui.transactions.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.ui.platform.LocalContext
import io.netscope.export.NetScopeExporter
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.netscope.model.SortOrder
import io.netscope.model.NetTransaction
import io.netscope.model.TransactionStats
import io.netscope.model.TransactionSummary
import io.netscope.ui.component.ColorAmber
import io.netscope.ui.component.ColorPurple
import io.netscope.ui.component.ColorSuccess
import io.netscope.ui.component.NetScopeAccent
import io.netscope.ui.component.NetScopeBorder
import io.netscope.ui.component.NetScopeCard
import io.netscope.ui.component.NetScopeDarkBackground
import io.netscope.ui.component.NetScopeDropdown
import io.netscope.ui.component.NetScopeMuted
import io.netscope.ui.component.NetScopeOnSurface
import io.netscope.ui.component.NetScopeSurface
import io.netscope.ui.component.TransactionItem
import io.netscope.ui.component.formatBytes
import io.netscope.ui.component.scrollEdgeFade
import io.netscope.ui.component.scrollIndicator
import io.netscope.ui.component.color
import io.netscope.ui.mvi.UiIntent
import io.netscope.ui.mvi.UiState
import io.netscope.ui.transactions.TransactionsMenu
import io.netscope.ui.transactions.TransactionsUiIntent
import io.netscope.ui.transactions.TransactionsUiState
import io.netscope.ui.transactions.model.TransactionFilterUi
import kotlinx.coroutines.launch

private val STATUS_FILTER_CHIPS = listOf(
    "2xx" to setOf(NetTransaction.StatusClass.SUCCESS),
    "3xx" to setOf(NetTransaction.StatusClass.REDIRECT),
    "4xx" to setOf(NetTransaction.StatusClass.CLIENT_ERROR),
    "5xx" to setOf(NetTransaction.StatusClass.SERVER_ERROR),
    "ERR" to setOf(NetTransaction.StatusClass.ERROR),
)

private val METHOD_CHIPS = listOf("GET", "POST", "PUT", "PATCH", "DELETE")

/** How far down the list, in rows, the jump back to the top appears. */
private const val JUMP_TO_TOP_FROM_INDEX = 5

/** A longer jump snaps this close to the top first, so it does not compose every row on the way. */
private const val JUMP_SNAP_INDEX = 10

private val PILL_SLIDE = spring(
    stiffness = Spring.StiffnessMedium,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)
private val ROW_FADE_IN = tween<Float>(durationMillis = 150)
private val ROW_PLACEMENT = spring(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionListScreen(
    uiState: UiState<TransactionsUiState>,
    onIntent: (UiIntent) -> Unit,
    onOpenTransaction: (Long) -> Unit,
) {
    val state = uiState.dataOrNull() ?: return
    val content = state.content
    val transactions = content.transactions
    val searchQuery = content.query
    val filterState = content.filter
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // The list starts from its top whenever what it shows is changed
    val onFilterIntent: (UiIntent) -> Unit = { intent ->
        onIntent(intent)
        scope.launch { listState.scrollToItem(index = 0) }
    }

    if (state is TransactionsUiState.Navigating) {
        LaunchedEffect(key1 = state.openedTransactionId) {
            onOpenTransaction(state.openedTransactionId)
            onIntent(TransactionsUiIntent.NavigationHandled)
        }
    }

    if (state is TransactionsUiState.SharingHar) {
        LaunchedEffect(key1 = state.har) {
            NetScopeExporter.share(
                context = context,
                text = state.har,
                chooserTitle = "Export as HAR",
                fileName = "netscope.har",
            )
            onIntent(TransactionsUiIntent.ShareHandled)
        }
    }

    Scaffold(
        // The bottom bar of the graph already keeps clear of the navigation bar
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    val accent = NetScopeAccent
                    val onSurface = NetScopeOnSurface
                    val dotTransition = rememberInfiniteTransition(label = "liveDot")
                    val dotAlpha by dotTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.4f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1400, easing = EaseInOutSine),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "dotAlpha",
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                // Read in the layer: the pulse must not recompose the bar every frame
                                .graphicsLayer { alpha = dotAlpha }
                                .background(accent, CircleShape),
                        )
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = accent)) { append("Net") }
                                withStyle(SpanStyle(color = onSurface)) { append("Scope") }
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = (-0.3).sp,
                        )
                    }
                },
                actions = {
                    // Clear filters — only when active
                    AnimatedVisibility(
                        visible = content.isFiltering,
                        enter = scaleIn(tween(150)) + fadeIn(tween(150)),
                        exit = fadeOut(tween(100)),
                    ) {
                        IconButton(onClick = { onFilterIntent(TransactionsUiIntent.ClearFiltersClicked) }) {
                            Icon(
                                imageVector = Icons.Default.FilterListOff,
                                contentDescription = "Clear filters",
                                tint = NetScopeAccent,
                            )
                        }
                    }
                    // Sort
                    Box {
                        IconButton(onClick = { onIntent(TransactionsUiIntent.MenuClicked(menu = TransactionsMenu.SORT)) }) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort",
                                tint = if (filterState.sortOrder != SortOrder.TIME) NetScopeAccent
                                       else NetScopeMuted,
                            )
                        }
                        DropdownMenu(
                            expanded = content.openMenu == TransactionsMenu.SORT,
                            onDismissRequest = { onIntent(TransactionsUiIntent.MenuDismissed) },
                            containerColor = NetScopeDropdown,
                        ) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = order.label(),
                                            color = if (filterState.sortOrder == order) NetScopeAccent
                                                    else NetScopeOnSurface,
                                            fontSize = 13.sp,
                                        )
                                    },
                                    trailingIcon = {
                                        if (filterState.sortOrder == order) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = NetScopeAccent,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    },
                                    onClick = { onFilterIntent(TransactionsUiIntent.SortOrderSelected(sortOrder = order)) },
                                )
                            }
                        }
                    }
                    // Export
                    Box {
                        IconButton(onClick = { onIntent(TransactionsUiIntent.MenuClicked(menu = TransactionsMenu.EXPORT)) }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = NetScopeMuted,
                            )
                        }
                        DropdownMenu(
                            expanded = content.openMenu == TransactionsMenu.EXPORT,
                            onDismissRequest = { onIntent(TransactionsUiIntent.MenuDismissed) },
                            containerColor = NetScopeDropdown,
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export all as HAR", color = NetScopeOnSurface, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = NetScopeMuted, modifier = Modifier.size(16.dp))
                                },
                                enabled = transactions.isNotEmpty(),
                                onClick = { onIntent(TransactionsUiIntent.ExportClicked) },
                            )
                        }
                    }
                    // Clear all
                    IconButton(onClick = { onIntent(TransactionsUiIntent.ClearAllClicked) }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear all",
                            tint = NetScopeMuted,
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
            // Search bar
            SearchBar(
                query = searchQuery,
                onQueryChange = { onFilterIntent(TransactionsUiIntent.QueryTyped(query = it)) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Filter chips — single scrollable row
            FilterBar(
                filterState = filterState,
                onMethodToggle = { onFilterIntent(TransactionsUiIntent.MethodToggled(method = it)) },
                onStatusToggle = { onFilterIntent(TransactionsUiIntent.StatusClassToggled(statusClass = it)) },
            )

            // List / empty state
            when {
                content.isLoading -> Unit
                transactions.isEmpty() -> EmptyState(isSearching = content.isFiltering)
                else -> TransactionList(
                    transactions = transactions,
                    stats = content.stats,
                    showsNewestFirst = filterState.sortOrder == SortOrder.TIME,
                    listState = listState,
                    onClick = { id -> onIntent(TransactionsUiIntent.TransactionClicked(id = id)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ── List ──────────────────────────────────────────────────────────────────────

/**
 * The transactions under their figures. Those are the first item, which anchors the list: at the
 * top new rows appear below them, further down the rows being read stay in place and a pill counts
 * the new ones.
 */
@Composable
private fun TransactionList(
    transactions: List<TransactionSummary>,
    stats: TransactionStats,
    showsNewestFirst: Boolean,
    listState: LazyListState,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEdgeFade(state = listState, background = NetScopeDarkBackground)
                .scrollIndicator(state = listState),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            item(key = "stats", contentType = "stats") {
                StatsBar(stats = stats)
            }
            items(transactions, key = { it.id }, contentType = { "transaction" }) { transaction ->
                TransactionItem(
                    transaction = transaction,
                    onClick = { onClick(transaction.id) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = ROW_FADE_IN,
                        placementSpec = ROW_PLACEMENT,
                        fadeOutSpec = null,
                    ),
                )
            }
        }

        JumpToTop(
            transactions = transactions,
            showsNewestFirst = showsNewestFirst,
            listState = listState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
        )
    }
}

/**
 * The pill back to the top, and what it counts. It is apart from the list because it follows the
 * scroll position: read there, that would recompose the list as it scrolls.
 */
@Composable
private fun JumpToTop(
    transactions: List<TransactionSummary>,
    showsNewestFirst: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val isAtTop by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val isFarDown by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > JUMP_TO_TOP_FROM_INDEX }
    }

    // Ids only grow, so what is newer than the newest row seen at the top has not been seen
    var newestSeenId by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(isAtTop, transactions) {
        if (isAtTop) transactions.maxOfOrNull { it.id }?.let { newestSeenId = it }
    }
    val newCount = if (showsNewestFirst && !isAtTop) transactions.count { it.id > newestSeenId } else 0

    AnimatedVisibility(
        visible = newCount > 0 || isFarDown,
        enter = fadeIn(tween(150)) + slideInVertically(PILL_SLIDE) { -it },
        exit = fadeOut(tween(120)) + slideOutVertically(PILL_SLIDE) { -it },
        modifier = modifier,
    ) {
        JumpToTopPill(
            newCount = newCount,
            onClick = { scope.launch { listState.jumpToTop() } },
        )
    }
}

private suspend fun LazyListState.jumpToTop() {
    if (firstVisibleItemIndex > JUMP_SNAP_INDEX) scrollToItem(index = JUMP_SNAP_INDEX)
    animateScrollToItem(index = 0)
}

@Composable
private fun JumpToTopPill(newCount: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(NetScopeDropdown)
            .border(0.5.dp, NetScopeAccent.copy(alpha = 0.5f), shape)
            .clickable(onClickLabel = "Scroll to the top", onClick = onClick)
            .padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = null,
            tint = NetScopeAccent,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = if (newCount > 0) "$newCount new" else "Top",
            color = NetScopeAccent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// ── Stats bar ─────────────────────────────────────────────────────────────────

@Composable
private fun StatsBar(stats: TransactionStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatCard(value = "${stats.totalCount}", label = "REQUESTS", valueColor = NetScopeAccent)
        StatCard(value = "${stats.errorCount}", label = "ERRORS", valueColor = if (stats.errorCount > 0) ColorAmber else NetScopeMuted)
        StatCard(value = stats.avgDurationMs?.let { "${it}ms" } ?: "—", label = "AVG TIME", valueColor = if (stats.avgDurationMs != null) ColorSuccess else NetScopeMuted)
        StatCard(value = stats.totalBytes.formatBytes(), label = "TOTAL", valueColor = ColorPurple)
    }
}

@Composable
private fun RowScope.StatCard(value: String, label: String, valueColor: Color) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(NetScopeCard)
            .border(0.5.dp, NetScopeBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            color = valueColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = label,
            color = NetScopeMuted,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
        )
    }
}

// ── Filter bar — single scrollable row ───────────────────────────────────────

@Composable
private fun FilterBar(
    filterState: TransactionFilterUi,
    onMethodToggle: (String) -> Unit,
    onStatusToggle: (NetTransaction.StatusClass) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Method chips
        METHOD_CHIPS.forEach { method ->
            val active = method in filterState.methods
            FilterChip(
                label = method,
                active = active,
                onClick = { onMethodToggle(method) },
            )
        }

        // Visual divider between methods and status classes
        Box(
            modifier = Modifier
                .height(18.dp)
                .width(1.dp)
                .background(NetScopeBorder),
        )

        // Status chips
        STATUS_FILTER_CHIPS.forEach { (label, classes) ->
            val active = filterState.statusClasses.containsAll(classes)
            FilterChip(
                label = label,
                active = active,
                onClick = { classes.forEach { onStatusToggle(it) } },
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val accent = NetScopeAccent
    val bg     = if (active) accent.copy(alpha = 0.15f) else NetScopeCard
    val border = if (active) accent.copy(alpha = 0.6f)  else NetScopeBorder
    val text   = if (active) accent                     else NetScopeMuted

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(0.5.dp, border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = text,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// ── Search bar ────────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NetScopeCard)
            .height(40.dp) // Fixed: the clear button must not resize the bar when it appears
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = TextStyle(
                color = NetScopeOnSurface,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            ),
            cursorBrush = SolidColor(NetScopeAccent),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = NetScopeMuted,
                        modifier = Modifier.size(16.dp),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search URL, headers, body…",
                                color = NetScopeMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        innerTextField()
                    }
                    // Clear button — only when there's text
                    AnimatedVisibility(
                        visible = query.isNotEmpty(),
                        enter = scaleIn(tween(120)) + fadeIn(tween(120)),
                        exit = fadeOut(tween(80)),
                    ) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = NetScopeMuted,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            },
        )
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(isSearching: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Icon in a rounded container
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(NetScopeCard),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isSearching) Icons.Outlined.SearchOff else Icons.Outlined.Hub,
                contentDescription = null,
                tint = NetScopeMuted,
                modifier = Modifier.size(38.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = if (isSearching) "No results found" else "No requests yet",
            color = NetScopeOnSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = if (isSearching) "Try adjusting your search term or clearing the filters"
                   else "Make an API call — it will appear here instantly",
            color = NetScopeMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 44.dp),
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun SortOrder.label(): String = when (this) {
    SortOrder.TIME     -> "Time (newest first)"
    SortOrder.DURATION -> "Duration (slowest first)"
    SortOrder.SIZE     -> "Size (largest first)"
}
