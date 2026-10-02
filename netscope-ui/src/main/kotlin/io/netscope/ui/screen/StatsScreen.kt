package io.netscope.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.netscope.model.NetTransaction
import io.netscope.ui.component.ColorAmber
import io.netscope.ui.component.ColorError
import io.netscope.ui.component.ColorPink
import io.netscope.ui.component.ColorPurple
import io.netscope.ui.component.ColorSuccess
import io.netscope.ui.component.MethodChip
import io.netscope.ui.component.NetScopeAccent
import io.netscope.ui.component.NetScopeBorder
import io.netscope.ui.component.NetScopeCard
import io.netscope.ui.component.NetScopeCardAlt
import io.netscope.ui.component.NetScopeDarkBackground
import io.netscope.ui.component.NetScopeMuted
import io.netscope.ui.component.NetScopeOnSurface
import io.netscope.ui.component.NetScopeSurface
import io.netscope.ui.component.NetScopeText2
import io.netscope.ui.component.color
import io.netscope.ui.component.formatBytes
import io.netscope.ui.component.methodColor
import io.netscope.ui.viewmodel.DurationBucket
import io.netscope.ui.viewmodel.NetScopeViewModel
import io.netscope.ui.viewmodel.StatsData
import kotlin.math.roundToInt

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: NetScopeViewModel,
    onTransactionClick: (Long) -> Unit,
) {
    // Computed off the main thread in the ViewModel — null until the first result lands.
    val statsOrNull by viewModel.stats.collectAsStateWithLifecycle()

    // Capture composable color tokens before lambdas / remember blocks
    val accent    = NetScopeAccent
    val onSurface = NetScopeOnSurface

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = accent))    { append("Net") }
                            withStyle(SpanStyle(color = onSurface)) { append("Scope") }
                            withStyle(SpanStyle(color = accent, fontSize = 13.sp)) { append("  Analytics") }
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize   = 17.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NetScopeSurface),
            )
        },
        containerColor = NetScopeDarkBackground,
    ) { innerPadding ->
        val stats = statsOrNull
        if (stats == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding))
        } else if (stats.totalCount == 0) {
            StatsEmptyState(modifier = Modifier.padding(innerPadding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── Summary cards ─────────────────────────────────────────────
                SummaryRow(stats)

                // ── Percentile performance ────────────────────────────────────
                StatsCard(title = "Performance") {
                    PercentileRow(
                        label = "p50 (median)",
                        value = stats.p50Ms?.let { "${it}ms" } ?: "—",
                        valueColor = when {
                            stats.p50Ms == null   -> NetScopeMuted
                            stats.p50Ms!! < 300L  -> ColorSuccess
                            stats.p50Ms!! < 1000L -> ColorAmber
                            else                  -> ColorError
                        },
                    )
                    HorizontalDivider(color = NetScopeBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                    PercentileRow(
                        label = "p95 (tail)",
                        value = stats.p95Ms?.let { "${it}ms" } ?: "—",
                        valueColor = when {
                            stats.p95Ms == null   -> NetScopeMuted
                            stats.p95Ms!! < 300L  -> ColorSuccess
                            stats.p95Ms!! < 1000L -> ColorAmber
                            else                  -> ColorError
                        },
                    )
                    HorizontalDivider(color = NetScopeBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                    PercentileRow(
                        label      = "Avg",
                        value      = stats.avgDurationMs?.let { "${it}ms" } ?: "—",
                        valueColor = if (stats.avgDurationMs != null) ColorSuccess else NetScopeMuted,
                    )
                    HorizontalDivider(color = NetScopeBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                    PercentileRow(
                        label      = "Total transferred",
                        value      = stats.totalBytes.formatBytes(),
                        valueColor = ColorPurple,
                    )
                }

                // ── Status distribution ───────────────────────────────────────
                if (stats.statusClassCounts.isNotEmpty()) {
                    StatsCard(title = "Status distribution") {
                        StatusDistributionBar(stats)
                        Spacer(Modifier.height(10.dp))
                        stats.statusClassCounts.forEach { (cls, count) ->
                            StatusClassRow(cls, count, stats.totalCount)
                        }
                    }
                }

                // ── HTTP methods ──────────────────────────────────────────────
                if (stats.methodCounts.isNotEmpty()) {
                    StatsCard(title = "HTTP methods") {
                        val maxCount = stats.methodCounts.maxOfOrNull { it.second } ?: 1
                        stats.methodCounts.forEach { (method, count) ->
                            MethodRow(method, count, maxCount, stats.totalCount)
                        }
                    }
                }

                // ── Response time distribution ────────────────────────────────
                if (stats.durationBuckets.isNotEmpty()) {
                    StatsCard(title = "Response time") {
                        val maxBucket = stats.durationBuckets.maxOfOrNull { it.count } ?: 1
                        stats.durationBuckets.forEach { bucket ->
                            BucketRow(bucket, maxBucket, stats.totalCount)
                        }
                    }
                }

                // ── Slowest requests ──────────────────────────────────────────
                if (stats.slowestRequests.isNotEmpty()) {
                    StatsCard(title = "Slowest requests") {
                        stats.slowestRequests.forEachIndexed { idx, tx ->
                            SlowestRow(
                                rank    = idx + 1,
                                tx      = tx,
                                onClick = { onTransactionClick(tx.id) },
                            )
                            if (idx < stats.slowestRequests.lastIndex) {
                                HorizontalDivider(
                                    color     = NetScopeBorder.copy(alpha = 0.5f),
                                    thickness = 0.5.dp,
                                )
                            }
                        }
                    }
                }

                // ── Top hosts ─────────────────────────────────────────────────
                if (stats.topHosts.isNotEmpty()) {
                    StatsCard(title = "Top hosts") {
                        val maxCount = stats.topHosts.maxOfOrNull { it.second } ?: 1
                        stats.topHosts.forEachIndexed { idx, (host, count) ->
                            HostRow(host, count, maxCount, stats.totalCount)
                            if (idx < stats.topHosts.lastIndex) {
                                HorizontalDivider(
                                    color     = NetScopeBorder.copy(alpha = 0.5f),
                                    thickness = 0.5.dp,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── Summary Row ───────────────────────────────────────────────────────────────

@Composable
private fun SummaryRow(stats: StatsData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SummaryCard(
            value      = "${stats.totalCount}",
            label      = "TOTAL",
            valueColor = NetScopeAccent,
            modifier   = Modifier.weight(1f),
        )
        SummaryCard(
            value      = "${(stats.successRate * 100).roundToInt()}%",
            label      = "SUCCESS",
            valueColor = ColorSuccess,
            modifier   = Modifier.weight(1f),
        )
        SummaryCard(
            value      = "${stats.errorCount}",
            label      = "ERRORS",
            valueColor = if (stats.errorCount > 0) ColorAmber else NetScopeMuted,
            modifier   = Modifier.weight(1f),
        )
        SummaryCard(
            value      = stats.avgDurationMs?.let { "${it}ms" } ?: "—",
            label      = "AVG",
            valueColor = if (stats.avgDurationMs != null) ColorSuccess else NetScopeMuted,
            modifier   = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryCard(
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
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text       = value,
            color      = valueColor,
            fontSize   = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text          = label,
            color         = NetScopeMuted,
            fontSize      = 7.sp,
            letterSpacing = 0.8.sp,
            fontFamily    = FontFamily.SansSerif,
        )
    }
}

// ── Stats section card ────────────────────────────────────────────────────────

@Composable
private fun StatsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NetScopeCard)
            .border(0.5.dp, NetScopeBorder, RoundedCornerShape(12.dp)),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
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
        }
        HorizontalDivider(color = NetScopeBorder, thickness = 0.5.dp)
        // Content
        content()
    }
}

// ── Performance percentile row ────────────────────────────────────────────────

@Composable
private fun PercentileRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = NetScopeText2, fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
        Text(
            text       = value,
            color      = valueColor,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// ── Status distribution ───────────────────────────────────────────────────────

@Composable
private fun StatusDistributionBar(stats: StatsData) {
    if (stats.statusClassCounts.isEmpty()) return
    val total = stats.statusClassCounts.sumOf { it.second }.coerceAtLeast(1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 10.dp)
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp)),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        stats.statusClassCounts.forEachIndexed { i, (cls, count) ->
            val shape = when {
                stats.statusClassCounts.size == 1 -> RoundedCornerShape(6.dp)
                i == 0 -> RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)
                i == stats.statusClassCounts.lastIndex -> RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)
                else -> RoundedCornerShape(0.dp)
            }
            Box(
                modifier = Modifier
                    .weight(count.toFloat() / total)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(cls.color()),
            )
        }
    }
}

@Composable
private fun StatusClassRow(
    cls: NetTransaction.StatusClass,
    count: Int,
    total: Int,
) {
    val pct = if (total > 0) (count * 100f / total).roundToInt() else 0
    val label = when (cls) {
        NetTransaction.StatusClass.SUCCESS      -> "2xx Success"
        NetTransaction.StatusClass.REDIRECT     -> "3xx Redirect"
        NetTransaction.StatusClass.CLIENT_ERROR -> "4xx Client error"
        NetTransaction.StatusClass.SERVER_ERROR -> "5xx Server error"
        NetTransaction.StatusClass.ERROR        -> "Network error"
        NetTransaction.StatusClass.PENDING      -> "Pending"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(cls.color()),
        )
        Text(
            text     = label,
            color    = NetScopeText2,
            fontSize = 11.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.weight(1f),
        )
        Text(
            text       = "$pct%",
            color      = NetScopeMuted,
            fontSize   = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text       = "$count",
            color      = cls.color(),
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier   = Modifier.width(36.dp),
        )
    }
    HorizontalDivider(color = NetScopeBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
}

// ── HTTP method row ───────────────────────────────────────────────────────────

@Composable
private fun MethodRow(method: String, count: Int, maxCount: Int, total: Int) {
    val color = method.methodColor()
    val pct   = if (total > 0) (count * 100f / total).roundToInt() else 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Method label
        Box(
            modifier = Modifier
                .width(52.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.12f))
                .border(0.5.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = method,
                color      = color,
                fontSize   = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        // Bar track
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(NetScopeCardAlt),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(count.toFloat() / maxCount.coerceAtLeast(1))
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
        // Pct
        Text(
            text     = "$pct%",
            color    = NetScopeMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(30.dp),
        )
        // Count
        Text(
            text       = "$count",
            color      = color,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier   = Modifier.width(30.dp),
        )
    }
    HorizontalDivider(color = NetScopeBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
}

// ── Duration bucket row ───────────────────────────────────────────────────────

@Composable
private fun BucketRow(bucket: DurationBucket, maxCount: Int, total: Int) {
    val pct = if (total > 0) (bucket.count * 100f / total).roundToInt() else 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text     = bucket.label,
            color    = NetScopeText2,
            fontSize = 10.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.width(72.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(NetScopeCardAlt),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(bucket.count.toFloat() / maxCount.coerceAtLeast(1))
                    .clip(RoundedCornerShape(3.dp))
                    .background(bucket.color),
            )
        }
        Text(
            text     = "$pct%",
            color    = NetScopeMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(30.dp),
        )
        Text(
            text       = "${bucket.count}",
            color      = bucket.color,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier   = Modifier.width(28.dp),
        )
    }
    HorizontalDivider(color = NetScopeBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
}

// ── Slowest request row ───────────────────────────────────────────────────────

@Composable
private fun SlowestRow(rank: Int, tx: NetTransaction, onClick: () -> Unit) {
    val durationColor = when {
        (tx.durationMs ?: 0L) <= 300L  -> ColorSuccess
        (tx.durationMs ?: 0L) <= 1000L -> ColorAmber
        else                            -> ColorError
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Rank
        Text(
            text       = "#$rank",
            color      = NetScopeMuted,
            fontSize   = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier   = Modifier.width(22.dp),
        )
        // Method chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(tx.method.methodColor().copy(alpha = 0.12f))
                .padding(horizontal = 5.dp, vertical = 2.dp),
        ) {
            Text(
                text       = tx.method,
                color      = tx.method.methodColor(),
                fontSize   = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        // Path
        Text(
            text     = tx.path,
            color    = NetScopeOnSurface,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Duration badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(durationColor.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text       = "${tx.durationMs}ms",
                color      = durationColor,
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

// ── Host row ──────────────────────────────────────────────────────────────────

@Composable
private fun HostRow(host: String, count: Int, maxCount: Int, total: Int) {
    val pct    = if (total > 0) (count * 100f / total).roundToInt() else 0
    val accent = NetScopeAccent   // capture @Composable token before remember { }
    val palette = listOf(accent, ColorPurple, ColorPink, ColorAmber, ColorSuccess)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Host favicon dot (colored by hash)
        val dotColor = remember(host) {
            palette[host.hashCode().and(0x7FFF_FFFF) % palette.size]
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        // Host text
        Text(
            text     = host,
            color    = NetScopeOnSurface,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Mini bar
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(NetScopeCardAlt),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(count.toFloat() / maxCount.coerceAtLeast(1))
                    .clip(RoundedCornerShape(3.dp))
                    .background(dotColor),
            )
        }
        Text(
            text       = "$pct%",
            color      = NetScopeMuted,
            fontSize   = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier   = Modifier.width(28.dp),
        )
        Text(
            text       = "$count",
            color      = NetScopeText2,
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            modifier   = Modifier.width(26.dp),
        )
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun StatsEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier         = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(NetScopeCard)
                    .border(0.5.dp, NetScopeBorder, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Outlined.AutoGraph,
                    contentDescription = null,
                    tint               = NetScopeMuted,
                    modifier           = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text       = "No data yet",
                color      = NetScopeOnSurface,
                fontSize   = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text     = "Make some requests — analytics will appear here",
                color    = NetScopeMuted,
                fontSize = 13.sp,
            )
        }
    }
}
