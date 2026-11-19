package io.netscope.ui.detail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import io.netscope.model.NetHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSearchTest {

    private val colors = SearchColors(match = Color.Yellow, current = Color.Red, onCurrent = Color.Black)

    @Test
    fun `should find matches in headers then body in reading order ignoring case`() {
        // Given
        val headers = listOf(NetHeader(name = "X-Token", value = "abc token"))
        val lines = listOf("{", "  \"token\": \"TOKEN\"", "}")

        // When
        val result = findMatches(headers = headers, lines = lines, query = "token")

        // Then
        assertEquals(
            listOf(
                SearchMatch.InHeader(header = 0, inValue = false, start = 2),
                SearchMatch.InHeader(header = 0, inValue = true, start = 4),
                SearchMatch.InBody(line = 1, start = 3),
                SearchMatch.InBody(line = 1, start = 12),
            ),
            result,
        )
    }

    @Test
    fun `should find no match when the query is empty`() {
        // When
        val result = findMatches(headers = listOf(NetHeader(name = "a", value = "b")), lines = listOf("a"), query = "")

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `should not count overlapping matches`() {
        // When
        val result = findMatches(headers = emptyList(), lines = listOf("aaaa"), query = "aa")

        // Then
        assertEquals(listOf(SearchMatch.InBody(line = 0, start = 0), SearchMatch.InBody(line = 0, start = 2)), result)
    }

    @Test
    fun `should stop counting at the most matches`() {
        // Given
        val lines = List(size = 10) { "a".repeat(MAX_LINE_CHARS) }

        // When
        val result = findMatches(headers = emptyList(), lines = lines, query = "a")

        // Then
        assertEquals(MAX_SEARCH_MATCHES, result.size)
    }

    @Test
    fun `should mark every match and the current one more strongly`() {
        // Given
        val text = AnnotatedString("id id")

        // When
        val result = text.withMatches(query = "ID", currentStart = 3, colors = colors)

        // Then
        assertEquals("id id", result.text)
        assertEquals(
            listOf(
                AnnotatedString.Range(item = SpanStyle(background = Color.Yellow), start = 0, end = 2),
                AnnotatedString.Range(item = SpanStyle(background = Color.Red, color = Color.Black), start = 3, end = 5),
            ),
            result.spanStyles,
        )
    }

    @Test
    fun `should keep the text unchanged when nothing matches`() {
        // Given
        val text = AnnotatedString("body")

        // When
        val result = text.withMatches(query = "zz", currentStart = null, colors = colors)

        // Then
        assertSame(text, result)
    }
}
