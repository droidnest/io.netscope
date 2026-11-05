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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.netscope.model.NetTransaction
import io.netscope.model.TransactionSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ChipShape = RoundedCornerShape(6.dp)
private val RowShape = RoundedCornerShape(10.dp)
private val StripeWidth = 3.dp

/** The title of a tab, the same on all of them. */
@Composable
internal fun ScreenTitle(text: String, subtitle: String? = null) {
    Column {
        Text(text = text, color = NetScopeOnSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) Text(text = subtitle, color = NetScopeText2, fontSize = 11.sp)
    }
}

// Rows are composed on the main thread only, and building a format for each costs more than the row
private val RowTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

// ── Chips ─────────────────────────────────────────────────────────────────────

@Composable
fun MethodChip(method: String, modifier: Modifier = Modifier) {
    val color = method.methodColor()
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), ChipShape)
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
    val label = when {
        code != null       -> code.toString()
        statusClass == NetTransaction.StatusClass.PENDING -> "···"
        else               -> "ERR"
    }
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), ChipShape)
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
    transaction: TransactionSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = transaction.statusClass.color()
    val isPending = transaction.statusClass == NetTransaction.StatusClass.PENDING
    val pulse = if (isPending) pendingPulse() else null

    val timeLabel = remember(transaction.requestDate) { RowTimeFormat.format(Date(transaction.requestDate)) }
    val sizeLabel = remember(transaction.totalSize) { transaction.totalSize.formatBytes() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(NetScopeCard)
            // Left accent stripe. Drawn, so it needs no intrinsic measuring of the row, and read
            // here so that the pulse redraws the stripe without recomposing the row.
            .drawBehind {
                drawRect(
                    color = statusColor.copy(alpha = pulse?.value ?: 1f),
                    size = Size(width = StripeWidth.toPx(), height = size.height),
                )
            }
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 11.dp, top = 10.dp, bottom = 10.dp),
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
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = timeLabel,
                    color = NetScopeMuted,
                    fontSize = 10.sp,
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
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(code = transaction.responseCode, statusClass = transaction.statusClass)
                if (transaction.durationMs != null) {
                    Text(
                        text = "${transaction.durationMs}ms",
                        color = durationColor(transaction.durationMs ?: 0L),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (transaction.totalSize > 0L) {
                    Text(
                        text = sizeLabel,
                        color = NetScopeMuted,
                        fontSize = 10.sp,
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

/** Pulsing opacity for in-flight requests. */
@Composable
private fun pendingPulse(): State<Float> =
    rememberInfiniteTransition(label = "pending").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pendingAlpha",
    )

// ── Helpers ───────────────────────────────────────────────────────────────────

fun Long.formatBytes(): String = when {
    this < 1_024L              -> "${this}B"
    this < 1_024L * 1_024L     -> "${"%.1f".format(this / 1_024.0)}KB"
    else                       -> "${"%.1f".format(this / (1_024.0 * 1_024.0))}MB"
}

/** Colours duration: green ≤300ms, amber ≤1s, red >1s. */
@Composable
private fun durationColor(ms: Long): Color = when {
    ms <= 300  -> ColorSuccess
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
