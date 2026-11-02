package io.netscope.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest

private val ThumbWidth = 3.dp
private val ThumbMinHeight = 32.dp
private val ThumbEndGap = 3.dp
private const val FADE_IN_MS = 120
private const val FADE_OUT_MS = 500
private const val THUMB_ALPHA = 0.7f

/**
 * A thin thumb showing where [state] is, visible while it scrolls. Nothing here reads the scroll
 * state while composing: the caller holds the list, and recomposing it would rebuild its content
 * every time a scroll starts. Item sizes are taken from the visible items.
 */
@Composable
internal fun Modifier.scrollIndicator(state: LazyListState): Modifier {
    val color = NetScopeMuted
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collectLatest { isScrolling ->
            alpha.animateTo(
                targetValue = if (isScrolling) THUMB_ALPHA else 0f,
                animationSpec = tween(durationMillis = if (isScrolling) FADE_IN_MS else FADE_OUT_MS),
            )
        }
    }
    return drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo
        if (alpha.value == 0f || visible.isEmpty() || visible.size >= info.totalItemsCount) return@drawWithContent

        val itemSize = visible.sumOf { it.size }.toFloat() / visible.size
        val contentHeight = (itemSize * info.totalItemsCount).coerceAtLeast(size.height + 1f)
        val thumbHeight = (size.height * size.height / contentHeight).coerceAtLeast(ThumbMinHeight.toPx())
        val scrolled = visible.first().index * itemSize + state.firstVisibleItemScrollOffset
        val progress = (scrolled / (contentHeight - size.height)).coerceIn(0f, 1f)
        val width = ThumbWidth.toPx()

        drawRoundRect(
            color = color,
            alpha = alpha.value,
            topLeft = Offset(
                x = size.width - width - ThumbEndGap.toPx(),
                y = progress * (size.height - thumbHeight),
            ),
            size = Size(width = width, height = thumbHeight),
            cornerRadius = CornerRadius(width / 2),
        )
    }
}
