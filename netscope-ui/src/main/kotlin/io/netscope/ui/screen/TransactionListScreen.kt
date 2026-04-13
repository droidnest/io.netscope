package io.netscope.ui.screen

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.netscope.domain.usecase.SortOrder
import io.netscope.model.NetTransaction
import io.netscope.ui.component.NetScopeAccent
import io.netscope.ui.component.NetScopeCard
import io.netscope.ui.component.NetScopeDarkBackground
import io.netscope.ui.component.NetScopeMuted
import io.netscope.ui.component.NetScopeSurface
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
) {
    val transactions by viewModel.transactions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "NetScope",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White,
                    )
                },
                actions = {
                    // Clear filters button — only visible when a filter is active
                    if (filterState.isActive || searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.clearFilters() }) {
                            Icon(
                                imageVector = Icons.Default.FilterListOff,
                                contentDescription = "Clear filters",
                                tint = NetScopeAccent,
                            )
                        }
                    }
                    // Sort menu
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort",
                                tint = if (filterState.sortOrder != SortOrder.TIME) NetScopeAccent else NetScopeMuted,
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false },
                            containerColor = Color(0xFF1A2235),
                        ) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = order.label(),
                                            color = if (filterState.sortOrder == order) NetScopeAccent else Color.White,
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
                    // Clear all transactions
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
            // ── Search bar ────────────────────────────────────────────────────
            SearchBar(
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // ── Filter chips ──────────────────────────────────────────────────
            FilterBar(filterState = filterState, viewModel = viewModel)

            // ── Request count ─────────────────────────────────────────────────
            if (transactions.isNotEmpty()) {
                Text(
                    text = "${transactions.size} request${if (transactions.size != 1) "s" else ""}",
                    color = NetScopeMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // ── List ──────────────────────────────────────────────────────────
            if (transactions.isEmpty()) {
                EmptyState(isSearching = searchQuery.isNotBlank() || filterState.isActive)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(transactions, key = { it.id }) { transaction ->
                        TransactionItem(
                            transaction = transaction,
                            onClick = { onTransactionClick(transaction.id) },
                        )
                    }
                }
            }
        }
    }
}

// ── Filter bar ────────────────────────────────────────────────────────────────

@Composable
private fun FilterBar(filterState: FilterState, viewModel: NetScopeViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NetScopeDarkBackground),
    ) {
        // Method chips
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            METHOD_CHIPS.forEach { method ->
                val active = method in filterState.methods
                val color = method.methodColor()
                FilterChip(
                    label = method,
                    active = active,
                    activeColor = color,
                    onClick = { viewModel.toggleMethodFilter(method) },
                )
            }
        }
        // Status class chips
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            STATUS_FILTER_CHIPS.forEach { (label, classes) ->
                val representative = classes.first()
                val active = filterState.statusClasses.containsAll(classes)
                val color = representative.color()
                FilterChip(
                    label = label,
                    active = active,
                    activeColor = color,
                    onClick = { classes.forEach { viewModel.toggleStatusClassFilter(it) } },
                )
            }
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
    val bgColor = if (active) activeColor.copy(alpha = 0.15f) else NetScopeCard
    val borderColor = if (active) activeColor.copy(alpha = 0.6f) else Color(0xFF1E2D45)
    val textColor = if (active) activeColor else NetScopeMuted

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(0.5.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = TextStyle(
                color = Color.White,
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
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        innerTextField()
                    }
                }
            },
        )
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(isSearching: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = if (isSearching) "🔍" else "📡", fontSize = 40.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isSearching) "No results found" else "Waiting for requests…",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isSearching) "Try a different search term or clear filters"
                else "Make an API call to see it here",
                color = NetScopeMuted,
                fontSize = 13.sp,
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun SortOrder.label(): String = when (this) {
    SortOrder.TIME     -> "Time (newest first)"
    SortOrder.DURATION -> "Duration (slowest first)"
    SortOrder.SIZE     -> "Size (largest first)"
}
