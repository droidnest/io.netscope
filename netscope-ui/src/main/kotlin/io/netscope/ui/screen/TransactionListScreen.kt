package io.netscope.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.ui.platform.LocalContext
import io.netscope.export.NetScopeExporter
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.netscope.domain.usecase.SortOrder
import io.netscope.model.NetTransaction
import io.netscope.ui.component.ColorError
import io.netscope.ui.component.ColorPending
import io.netscope.ui.component.NetScopeAccent
import io.netscope.ui.component.NetScopeBorder
import io.netscope.ui.component.NetScopeCard
import io.netscope.ui.component.NetScopeDarkBackground
import io.netscope.ui.component.NetScopeDropdown
import io.netscope.ui.component.NetScopeMuted
import io.netscope.ui.component.NetScopeOnSurface
import io.netscope.ui.component.NetScopeSurface
import io.netscope.ui.component.StatDot
import io.netscope.ui.component.TransactionItem
import io.netscope.ui.component.color
import io.netscope.ui.component.methodColor
import io.netscope.ui.viewmodel.FilterState
import io.netscope.ui.viewmodel.NetScopeViewModel

private val STATUS_FILTER_CHIPS = listOf(
    "2xx" to setOf(NetTransaction.StatusClass.SUCCESS),
    "3xx" to setOf(NetTransaction.StatusClass.REDIRECT),
    "4xx" to setOf(NetTransaction.StatusClass.CLIENT_ERROR),
    "5xx" to setOf(NetTransaction.StatusClass.SERVER_ERROR),
    "ERR" to setOf(NetTransaction.StatusClass.ERROR),
)

private val METHOD_CHIPS = listOf("GET", "POST", "PUT", "PATCH", "DELETE")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    viewModel: NetScopeViewModel,
    onTransactionClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val transactions by viewModel.transactions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    val context = LocalContext.current
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var exportMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Logo dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NetScopeAccent),
                        )
                        Text(
                            text = "NetScope",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = NetScopeOnSurface,
                            letterSpacing = (-0.3).sp,
                        )
                    }
                },
                actions = {
                    // Settings
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = NetScopeMuted,
                        )
                    }
                    // Clear filters — only when active
                    AnimatedVisibility(
                        visible = filterState.isActive || searchQuery.isNotBlank(),
                        enter = scaleIn(tween(150)) + fadeIn(tween(150)),
                        exit = fadeOut(tween(100)),
                    ) {
                        IconButton(onClick = { viewModel.clearFilters() }) {
                            Icon(
                                imageVector = Icons.Default.FilterListOff,
                                contentDescription = "Clear filters",
                                tint = NetScopeAccent,
                            )
                        }
                    }
                    // Sort
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort",
                                tint = if (filterState.sortOrder != SortOrder.TIME) NetScopeAccent
                                       else NetScopeMuted,
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false },
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
                                    onClick = {
                                        viewModel.setSortOrder(order)
                                        sortMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    // Export
                    Box {
                        IconButton(onClick = { exportMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = NetScopeMuted,
                            )
                        }
                        DropdownMenu(
                            expanded = exportMenuExpanded,
                            onDismissRequest = { exportMenuExpanded = false },
                            containerColor = NetScopeDropdown,
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export all as HAR", color = NetScopeOnSurface, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = NetScopeMuted, modifier = Modifier.size(16.dp))
                                },
                                enabled = transactions.isNotEmpty(),
                                onClick = {
                                    exportMenuExpanded = false
                                    NetScopeExporter.share(
                                        context = context,
                                        text = NetScopeExporter.toHar(transactions),
                                        chooserTitle = "Export as HAR",
                                    )
                                },
                            )
                        }
                    }
                    // Clear all
                    IconButton(onClick = { viewModel.clearAllTransactions() }) {
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
                onQueryChange = viewModel::onSearchQueryChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Filter chips — single scrollable row
            FilterBar(
                filterState = filterState,
                onMethodToggle = viewModel::toggleMethodFilter,
                onStatusToggle = viewModel::toggleStatusClassFilter,
            )

            // Stats summary — count · errors · avg time
            if (transactions.isNotEmpty()) {
                StatsBar(transactions)
            }

            HorizontalDivider(
                color = NetScopeBorder,
                thickness = 0.5.dp,
                modifier = Modifier.padding(top = 2.dp),
            )

            // List / empty state
            if (transactions.isEmpty()) {
                EmptyState(isSearching = searchQuery.isNotBlank() || filterState.isActive)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, top = 10.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(transactions, key = { it.id }) { transaction ->
                        TransactionItem(
                            transaction = transaction,
                            onClick = { onTransactionClick(transaction.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

// ── Stats bar ─────────────────────────────────────────────────────────────────

@Composable
private fun StatsBar(transactions: List<NetTransaction>) {
    val (errorCount, avgMs, pendingCount) = remember(transactions) {
        val errors = transactions.count {
            it.statusClass in setOf(
                NetTransaction.StatusClass.CLIENT_ERROR,
                NetTransaction.StatusClass.SERVER_ERROR,
                NetTransaction.StatusClass.ERROR,
            )
        }
        val durations = transactions.mapNotNull { it.durationMs }
        val avg = if (durations.isEmpty()) null else durations.average().toLong()
        val pending = transactions.count { it.statusClass == NetTransaction.StatusClass.PENDING }
        Triple(errors, avg, pending)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "${transactions.size} request${if (transactions.size != 1) "s" else ""}",
            color = NetScopeMuted,
            fontSize = 11.sp,
        )
        if (errorCount > 0) {
            StatDot()
            Text(text = "$errorCount error${if (errorCount != 1) "s" else ""}", color = ColorError, fontSize = 11.sp)
        }
        if (avgMs != null) {
            StatDot()
            Text(text = "avg ${avgMs}ms", color = NetScopeMuted, fontSize = 11.sp)
        }
        if (pendingCount > 0) {
            StatDot()
            Text(text = "$pendingCount pending", color = ColorPending, fontSize = 11.sp)
        }
    }
}

// ── Filter bar — single scrollable row ───────────────────────────────────────

@Composable
private fun FilterBar(
    filterState: FilterState,
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
                activeColor = method.methodColor(),
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
                activeColor = classes.first().color(),
                onClick = { classes.forEach { onStatusToggle(it) } },
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    val bg     = if (active) activeColor.copy(alpha = 0.15f) else NetScopeCard
    val border = if (active) activeColor.copy(alpha = 0.55f) else NetScopeBorder
    val text   = if (active) activeColor else NetScopeMuted

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(0.5.dp, border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
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
            .padding(horizontal = 12.dp, vertical = 9.dp),
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
                                fontSize = 13.sp,
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
                            modifier = Modifier.size(18.dp),
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
