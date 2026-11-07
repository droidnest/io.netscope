package io.netscope.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/** Every colour that carries small text has to reach WCAG AA where it is used, in both themes. */
class NetScopeColorsTest {

    private val themes = listOf(DarkNetScopeColors, LightNetScopeColors)

    private fun contrast(first: Color, second: Color): Float {
        val lighter = max(first.luminance(), second.luminance())
        val darker = min(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun assertReadable(name: String, text: Color, background: Color) {
        val ratio = contrast(text, background)
        assertTrue("$name is only $ratio:1", ratio >= MIN_CONTRAST)
    }

    private fun NetScopeColors.semantic() = listOf(success, warning, error, pending, purple, pink, cyan)

    @Test
    fun `should keep text readable on the cards and the background`() {
        themes.forEach { colors ->
            listOf(colors.onSurface, colors.text2, colors.muted, colors.accent).forEach { text ->
                assertReadable(name = "Text on card, dark=${colors.isDark}", text = text, background = colors.card)
                assertReadable(name = "Text on background, dark=${colors.isDark}", text = text, background = colors.background)
            }
        }
    }

    @Test
    fun `should keep a semantic colour readable on a card and on its own chip tint`() {
        themes.forEach { colors ->
            colors.semantic().forEach { color ->
                val tint = color.copy(alpha = CHIP_TINT).compositeOver(colors.card)
                assertReadable(name = "Semantic on card, dark=${colors.isDark}", text = color, background = colors.card)
                assertReadable(name = "Semantic on chip, dark=${colors.isDark}", text = color, background = tint)
            }
        }
    }

    @Test
    fun `should keep every syntax colour readable on the code surface`() {
        themes.forEach { colors ->
            with(colors.syntax) { listOf(key, string, number, keyword, punctuation, plain) }.forEach { color ->
                assertReadable(name = "Syntax, dark=${colors.isDark}", text = color, background = colors.code)
            }
        }
    }

    @Test
    fun `should keep content readable on a filled accent surface`() {
        themes.forEach { colors ->
            assertReadable(name = "On accent, dark=${colors.isDark}", text = colors.onAccent, background = colors.accent)
        }
    }

    private companion object {
        const val MIN_CONTRAST = 4.5f
        const val CHIP_TINT = 0.15f
    }
}
