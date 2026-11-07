package io.netscope.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/** Body and secondary text have to reach WCAG AA where they are drawn, in both themes. */
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

    @Test
    fun `should keep every syntax colour readable on the code surface`() {
        themes.forEach { colors ->
            with(colors.syntax) { listOf(key, string, number, keyword, punctuation, plain) }.forEach { color ->
                assertReadable(name = "Syntax, dark=${colors.isDark}", text = color, background = colors.code)
            }
        }
    }

    @Test
    fun `should keep light text readable on the cards and the background`() {
        with(LightNetScopeColors) {
            listOf(onSurface, text2, muted, accent).forEach { text ->
                assertReadable(name = "Text on card", text = text, background = card)
                assertReadable(name = "Text on background", text = text, background = background)
            }
        }
    }

    private companion object {
        const val MIN_CONTRAST = 4.5f
    }
}
