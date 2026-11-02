package io.netscope.ui.detail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/** The syntax a body is highlighted with. */
internal enum class BodyKind { JSON, XML, PLAIN }

/** A body cut into the lines a lazy list shows, so that only the visible ones cost anything. */
internal data class FormattedBody(val kind: BodyKind, val lines: List<String>)

/** Most characters on a line: a body with no line breaks would be one huge text otherwise. */
internal const val MAX_LINE_CHARS = 1_000

/** Pretty-prints [body] and cuts it into lines. A large body takes long: call it off the main thread. */
internal fun formatBody(body: String): FormattedBody {
    val trimmed = body.trim()
    return when {
        trimmed.startsWith("{") || trimmed.startsWith("[") ->
            FormattedBody(kind = BodyKind.JSON, lines = trimmed.prettyPrintJson().toDisplayLines())
        trimmed.startsWith("<") ->
            FormattedBody(kind = BodyKind.XML, lines = trimmed.replace("><", ">\n<").toDisplayLines())
        else ->
            FormattedBody(kind = BodyKind.PLAIN, lines = trimmed.toDisplayLines())
    }
}

/** This line in colour. A line is short enough to do this while composing it. */
internal fun String.highlighted(kind: BodyKind): AnnotatedString = buildAnnotatedString {
    when (kind) {
        BodyKind.JSON -> appendJsonHighlighted(json = this@highlighted)
        BodyKind.XML -> appendXmlHighlighted(xml = this@highlighted)
        BodyKind.PLAIN -> withStyle(SpanStyle(color = JsonDefaultColor)) { append(this@highlighted) }
    }
}

private fun String.toDisplayLines(): List<String> = buildList {
    lineSequence().forEach { line ->
        var start = 0
        while (line.length - start > MAX_LINE_CHARS) {
            val end = start + MAX_LINE_CHARS
            val cut = if (line[end - 1].isHighSurrogate()) end - 1 else end
            add(line.substring(start, cut))
            start = cut
        }
        add(line.substring(start))
    }
}

// ── JSON syntax highlighting ──────────────────────────────────────────────────

private val JsonKeyColor     = Color(0xFF7DD3FC)
private val JsonStringColor  = Color(0xFF86EFAC)
private val JsonNumberColor  = Color(0xFFFBBF24)
private val JsonKeywordColor = Color(0xFFA78BFA)
private val JsonPunctColor   = Color(0xFF6B7FA3)
private val JsonDefaultColor = Color(0xFFCDD6F4)

private fun AnnotatedString.Builder.appendJsonHighlighted(json: String) {
    var i = 0

    fun peekIsColon(): Boolean {
        var j = i
        while (j < json.length && json[j] in " \n\r\t") j++
        return j < json.length && json[j] == ':'
    }

    while (i < json.length) {
        val ch = json[i]
        when {
            ch == '"' -> {
                val start = i
                i++
                while (i < json.length) {
                    when {
                        json[i] == '\\' -> i += 2
                        json[i] == '"'  -> { i++; break }
                        else            -> i++
                    }
                }
                // A cut line can end on the backslash of an escape
                i = minOf(i, json.length)
                val color = if (peekIsColon()) JsonKeyColor else JsonStringColor
                withStyle(SpanStyle(color = color)) { append(json.substring(start, i)) }
            }
            ch == '-' || ch.isDigit() -> {
                val start = i
                if (ch == '-') i++
                while (i < json.length && (json[i].isDigit() || json[i] == '.' || json[i] in "eE+-")) i++
                withStyle(SpanStyle(color = JsonNumberColor)) { append(json.substring(start, i)) }
            }
            json.startsWith("true",  i) -> { withStyle(SpanStyle(color = JsonKeywordColor)) { append("true")  }; i += 4 }
            json.startsWith("false", i) -> { withStyle(SpanStyle(color = JsonKeywordColor)) { append("false") }; i += 5 }
            json.startsWith("null",  i) -> { withStyle(SpanStyle(color = JsonKeywordColor)) { append("null")  }; i += 4 }
            ch in "{}[],:." -> { withStyle(SpanStyle(color = JsonPunctColor)) { append(ch) }; i++ }
            else -> { withStyle(SpanStyle(color = JsonDefaultColor)) { append(ch) }; i++ }
        }
    }
}

// ── XML syntax highlighting ───────────────────────────────────────────────────

private fun AnnotatedString.Builder.appendXmlHighlighted(xml: String) {
    var i = 0
    while (i < xml.length) {
        when {
            xml.startsWith("<!--", i) -> {
                val end = xml.indexOf("-->", i + 4).let { if (it == -1) xml.length else it + 3 }
                withStyle(SpanStyle(color = JsonPunctColor)) { append(xml.substring(i, end)) }
                i = end
            }
            xml.startsWith("<?", i) -> {
                val end = xml.indexOf("?>", i + 2).let { if (it == -1) xml.length else it + 2 }
                withStyle(SpanStyle(color = JsonKeywordColor)) { append(xml.substring(i, end)) }
                i = end
            }
            xml[i] == '<' -> {
                withStyle(SpanStyle(color = JsonPunctColor)) { append('<') }
                i++
                if (i < xml.length && xml[i] == '/') {
                    withStyle(SpanStyle(color = JsonPunctColor)) { append('/') }
                    i++
                }
                val nameStart = i
                while (i < xml.length && xml[i] !in " \t\n\r>/") i++
                withStyle(SpanStyle(color = JsonKeyColor)) { append(xml.substring(nameStart, i)) }
                while (i < xml.length && xml[i] != '>' && !(xml[i] == '/' && i + 1 < xml.length && xml[i + 1] == '>')) {
                    if (xml[i].isWhitespace()) { append(xml[i++]) }
                    else {
                        val attrStart = i
                        while (i < xml.length && xml[i] !in "= \t\n\r>/") i++
                        withStyle(SpanStyle(color = JsonStringColor)) { append(xml.substring(attrStart, i)) }
                        if (i < xml.length && xml[i] == '=') {
                            withStyle(SpanStyle(color = JsonPunctColor)) { append('=') }
                            i++
                            if (i < xml.length && xml[i] == '"') {
                                val q = i++
                                while (i < xml.length && xml[i] != '"') i++
                                if (i < xml.length) i++
                                withStyle(SpanStyle(color = JsonNumberColor)) { append(xml.substring(q, i)) }
                            }
                        }
                    }
                }
                if (i < xml.length && xml[i] == '/') { withStyle(SpanStyle(color = JsonPunctColor)) { append('/') }; i++ }
                if (i < xml.length && xml[i] == '>') { withStyle(SpanStyle(color = JsonPunctColor)) { append('>') }; i++ }
            }
            else -> {
                val start = i
                while (i < xml.length && xml[i] != '<') i++
                withStyle(SpanStyle(color = JsonDefaultColor)) { append(xml.substring(start, i)) }
            }
        }
    }
}

private fun String.prettyPrintJson(): String = try {
    val sb = StringBuilder()
    var indent = 0
    var inString = false
    var i = 0
    while (i < length) {
        val ch = this[i]
        when {
            ch == '"' && !inString -> { inString = true;  sb.append(ch) }
            ch == '"' && inString  -> { inString = false; sb.append(ch) }
            ch == '\\' && inString -> { sb.append(ch); sb.append(this[++i]) }
            inString               -> sb.append(ch)
            ch == '{' || ch == '[' -> { sb.append(ch); sb.append('\n'); indent++; sb.append("  ".repeat(indent)) }
            ch == '}' || ch == ']' -> { sb.append('\n'); indent--; sb.append("  ".repeat(indent)); sb.append(ch) }
            ch == ','              -> { sb.append(ch); sb.append('\n'); sb.append("  ".repeat(indent)) }
            ch == ':'              -> sb.append(": ")
            ch in " \n\r\t"       -> { /* skip pre-existing whitespace */ }
            else                   -> sb.append(ch)
        }
        i++
    }
    sb.toString()
} catch (_: Exception) { this }
