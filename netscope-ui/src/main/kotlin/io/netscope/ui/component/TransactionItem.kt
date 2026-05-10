package io.netscope.ui.component

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Chips ─────────────────────────────────────────────────────────────────────

@Composable
fun MethodChip(method: String, modifier: Modifier = Modifier) {
    val color = method.methodColor()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = method,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
fun StatusBadge(code: Int?, statusClass: NetTransaction.StatusClass, modifier: Modifier = Modifier) {
    val color = statusClass.color()
    val label = when {
        code != null       -> code.toString()
        statusClass == NetTransaction.StatusClass.PENDING -> "···"
        else               -> "ERR"
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
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

// ── Transaction row ───────────────────────────────────────────────────────────

@Composable
fun TransactionItem(
    transaction: NetTransaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = transaction.statusClass.color()
    val isPending = transaction.statusClass == NetTransaction.StatusClass.PENDING

    // Pulsing opacity for in-flight requests
    val stripeAlpha = if (isPending) {
        val transition = rememberInfiniteTransition(label = "pending")
        val alpha by transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pendingAlpha",
        )
        alpha
    } else 1f

    val timeLabel = remember(transaction.requestDate) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(transaction.requestDate))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)       // lets the accent stripe stretch to content height
            .clip(RoundedCornerShape(10.dp))
            .background(NetScopeCard)
            .clickable(onClick = onClick),
    ) {
        // Left accent stripe — 3dp, full natural height
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(
                    color = statusColor.copy(alpha = stripeAlpha),
                    shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // ── Row 1: method · path · timestamp ─────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                MethodChip(method = transaction.method)
                Text(
                    text = transaction.path + (transaction.query?.let { "?$it" } ?: ""),
                    color = NetScopeOnSurface,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = timeLabel,
                    color = NetScopeMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            // ── Row 2: host · status · duration · size ────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = transaction.host,
                    color = NetScopeMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(code = transaction.responseCode, statusClass = transaction.statusClass)
                if (transaction.durationMs != null) {
                    Text(
                        text = "${transaction.durationMs}ms",
                        color = durationColor(transaction.durationMs ?: 0L),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (transaction.totalSize > 0L) {
                    Text(
                        text = transaction.totalSize.formatBytes(),
                        color = NetScopeMuted,
                        fontSize = 9.sp,
                    )
                }
            }

            // ── Row 3: error (only when present) ─────────────────────────
            if (transaction.error != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = ColorError,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = transaction.error.orEmpty(),
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
    this < 1_024L              -> "${this}B"
    this < 1_024L * 1_024L     -> "${"%.1f".format(this / 1_024.0)}KB"
    else                       -> "${"%.1f".format(this / (1_024.0 * 1_024.0))}MB"
}

/** Colours duration: green ≤300ms, amber ≤1s, red >1s. */
private fun durationColor(ms: Long): Color = when {
    ms <= 300  -> ColorSuccess.copy(alpha = 0.85f)
    ms <= 1000 -> ColorAmber
    else       -> ColorError
}

/** Subtle dot used as a separator between stat chips. */
@Composable
fun StatDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(3.dp)
            .clip(CircleShape)
            .background(NetScopeMuted.copy(alpha = 0.4f)),
    )
}
