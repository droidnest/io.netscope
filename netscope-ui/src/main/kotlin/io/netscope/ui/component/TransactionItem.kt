package io.netscope.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.netscope.model.NetTransaction

@Composable
fun MethodChip(method: String, modifier: Modifier = Modifier) {
    val color = method.methodColor()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = method,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
fun StatusBadge(code: Int?, statusClass: NetTransaction.StatusClass, modifier: Modifier = Modifier) {
    val color = statusClass.color()
    val label = code?.toString() ?: "···"
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
fun TransactionItem(
    transaction: NetTransaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = transaction.statusClass.color()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NetScopeCard)
            .border(
                width = 0.5.dp,
                color = NetScopeBorder,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(start = 0.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Status color stripe
            Box(
                modifier = Modifier
                    .padding(start = 0.dp)
                    .background(
                        color = statusColor,
                        shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp),
                    )
                    .padding(horizontal = 3.dp, vertical = 0.dp)
                    .height(72.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                // Top row: method + status + duration + size
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MethodChip(method = transaction.method)
                    StatusBadge(
                        code = transaction.responseCode,
                        statusClass = transaction.statusClass,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (transaction.durationMs != null) {
                        Text(
                            text = "${transaction.durationMs}ms",
                            color = NetScopeMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    if (transaction.totalSize > 0) {
                        Text(
                            text = transaction.totalSize.formatBytes(),
                            color = NetScopeMuted,
                            fontSize = 11.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Host
                Text(
                    text = transaction.host,
                    color = NetScopeMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Path
                Text(
                    text = transaction.path + (transaction.query?.let { "?$it" } ?: ""),
                    color = Color(0xFFCDD6F4),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Error pill
                if (transaction.error != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "⚠ ${transaction.error}",
                        color = ColorError,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

fun Long.formatBytes(): String = when {
    this < 1024 -> "${this}B"
    this < 1024 * 1024 -> "${"%.1f".format(this / 1024.0)}KB"
    else -> "${"%.1f".format(this / (1024.0 * 1024.0))}MB"
}
