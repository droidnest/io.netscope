package io.netscope.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lays its children out left to right and starts a new line when the next child does not fit the
 * available width. Lines are top-aligned and packed from the start, like `FlowRow` with
 * `Arrangement.spacedBy` on both axes.
 *
 * Built on the stable [Layout] API because `FlowRow` changed its binary signature between
 * Compose foundation 1.7 and 1.8: a library compiled against one crashes with `NoSuchMethodError`
 * in an app on the other.
 */
@Composable
internal fun WrapRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 0.dp,
    verticalSpacing: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val hGap = horizontalSpacing.roundToPx()
        val vGap = verticalSpacing.roundToPx()
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }

        val lines = mutableListOf<MutableList<Placeable>>()
        var lineWidth = 0
        placeables.forEach { placeable ->
            val line = lines.lastOrNull()
            if (line == null || lineWidth + hGap + placeable.width > constraints.maxWidth) {
                lines += mutableListOf(placeable)
                lineWidth = placeable.width
            } else {
                line += placeable
                lineWidth += hGap + placeable.width
            }
        }

        val lineHeights = lines.map { line -> line.maxOf { it.height } }
        val contentWidth = lines.maxOfOrNull { line -> line.sumOf { it.width } + hGap * (line.size - 1) } ?: 0
        val contentHeight = lineHeights.sum() + vGap * (lines.size - 1).coerceAtLeast(0)
        val width = contentWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(width, height) {
            var y = 0
            lines.forEachIndexed { index, line ->
                var x = 0
                line.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + hGap
                }
                y += lineHeights[index] + vGap
            }
        }
    }
}
