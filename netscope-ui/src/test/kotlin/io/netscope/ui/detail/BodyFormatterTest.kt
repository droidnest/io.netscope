package io.netscope.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyFormatterTest {

    @Test
    fun `should pretty print json on one line per value when formatting`() {
        // When
        val result = formatBody(body = """{"a":1,"b":[true]}""")

        // Then
        assertEquals(BodyKind.JSON, result.kind)
        assertEquals(listOf("{", "  \"a\": 1,", "  \"b\": [", "    true", "  ]", "}"), result.lines)
    }

    @Test
    fun `should give each xml tag its own line when formatting`() {
        // When
        val result = formatBody(body = "<a><b>text</b></a>")

        // Then
        assertEquals(BodyKind.XML, result.kind)
        assertEquals(listOf("<a>", "<b>text</b>", "</a>"), result.lines)
    }

    @Test
    fun `should keep every character when cutting a line with no breaks`() {
        // Given
        val body = "x".repeat(MAX_LINE_CHARS * 2 + 5)

        // When
        val result = formatBody(body = body)

        // Then
        assertEquals(BodyKind.PLAIN, result.kind)
        assertEquals(listOf(MAX_LINE_CHARS, MAX_LINE_CHARS, 5), result.lines.map { it.length })
        assertEquals(body, result.lines.joinToString(separator = ""))
    }

    @Test
    fun `should not split a surrogate pair when cutting a line`() {
        // Given
        val body = "x".repeat(MAX_LINE_CHARS - 1) + "😀" + "y"

        // When
        val result = formatBody(body = body)

        // Then
        assertEquals(MAX_LINE_CHARS - 1, result.lines.first().length)
        assertEquals("😀y", result.lines.last())
    }

    @Test
    fun `should show the raw text when a truncated json cannot be pretty printed`() {
        // Given
        val truncated = """{"a":"value\"""

        // When
        val result = formatBody(body = truncated)

        // Then
        assertEquals(listOf(truncated), result.lines)
    }

    @Test
    fun `should keep the text of a line that ends on an escape when highlighting`() {
        // Given
        val line = """{"a":"value\"""

        // When
        val result = line.highlighted(kind = BodyKind.JSON)

        // Then
        assertEquals(line, result.text)
    }

    @Test
    fun `should colour keys apart from string values when highlighting json`() {
        // When
        val result = """"key": "value"""".highlighted(kind = BodyKind.JSON)

        // Then
        val colours = result.spanStyles.filter { result.text.substring(it.start, it.end).startsWith('"') }
        assertEquals(2, colours.size)
        assertTrue(colours[0].item.color != colours[1].item.color)
    }

    @Test(timeout = 5_000)
    fun `should keep the text of a tag with a slash that closes nothing when highlighting xml`() {
        listOf("<a href=/x>", "<a /b>", "<a /", "<a b=/").forEach { line ->
            // When
            val result = line.highlighted(kind = BodyKind.XML)

            // Then
            assertEquals(line, result.text)
        }
    }

    @Test(timeout = 10_000)
    fun `should show the raw text when a body only opens brackets`() {
        // Given
        val body = "[".repeat(800_000)

        // When
        val result = formatBody(body = body)

        // Then
        assertEquals(body, result.lines.joinToString(separator = ""))
        assertEquals(800, result.lines.size)
    }
}
