package io.netscope.ui.detail

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import io.netscope.ui.component.SyntaxColors

/** The syntax a body is highlighted with. */
internal enum class BodyKind { JSON, XML, PLAIN }

/** A body cut into the lines a lazy list shows, so that only the visible ones cost anything. */
internal data class FormattedBody(val kind: BodyKind, val lines: List<String>)

/** Most characters on a line: a body with no line breaks would be one huge text otherwise. */
internal const val MAX_LINE_CHARS = 1_000

/** Most characters of a pretty-printed body, about four times what a large real one needs. */
private const val MAX_PRETTY_CHARS = 8_000_000

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
internal fun String.highlighted(kind: BodyKind, colors: SyntaxColors): AnnotatedString = buildAnnotatedString {
    when (kind) {
        BodyKind.JSON -> appendJsonHighlighted(json = this@highlighted, colors = colors)
        BodyKind.XML -> appendXmlHighlighted(xml = this@highlighted, colors = colors)
        BodyKind.PLAIN -> withStyle(SpanStyle(color = colors.plain)) { append(this@highlighted) }
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

private fun AnnotatedString.Builder.appendJsonHighlighted(json: String, colors: SyntaxColors) {
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
                val color = if (peekIsColon()) colors.key else colors.string
                withStyle(SpanStyle(color = color)) { append(json.substring(start, i)) }
            }
            ch == '-' || ch.isDigit() -> {
                val start = i
                if (ch == '-') i++
                while (i < json.length && (json[i].isDigit() || json[i] == '.' || json[i] in "eE+-")) i++
                withStyle(SpanStyle(color = colors.number)) { append(json.substring(start, i)) }
            }
            json.startsWith("true",  i) -> { withStyle(SpanStyle(color = colors.keyword)) { append("true")  }; i += 4 }
            json.startsWith("false", i) -> { withStyle(SpanStyle(color = colors.keyword)) { append("false") }; i += 5 }
            json.startsWith("null",  i) -> { withStyle(SpanStyle(color = colors.keyword)) { append("null")  }; i += 4 }
            ch in "{}[],:." -> { withStyle(SpanStyle(color = colors.punctuation)) { append(ch) }; i++ }
            else -> { withStyle(SpanStyle(color = colors.plain)) { append(ch) }; i++ }
        }
    }
}

// ── XML syntax highlighting ───────────────────────────────────────────────────

private fun AnnotatedString.Builder.appendXmlHighlighted(xml: String, colors: SyntaxColors) {
    var i = 0
    while (i < xml.length) {
        when {
            xml.startsWith("<!--", i) -> {
                val end = xml.indexOf("-->", i + 4).let { if (it == -1) xml.length else it + 3 }
                withStyle(SpanStyle(color = colors.punctuation)) { append(xml.substring(i, end)) }
                i = end
            }
            xml.startsWith("<?", i) -> {
                val end = xml.indexOf("?>", i + 2).let { if (it == -1) xml.length else it + 2 }
                withStyle(SpanStyle(color = colors.keyword)) { append(xml.substring(i, end)) }
                i = end
            }
            xml[i] == '<' -> {
                withStyle(SpanStyle(color = colors.punctuation)) { append('<') }
                i++
                if (i < xml.length && xml[i] == '/') {
                    withStyle(SpanStyle(color = colors.punctuation)) { append('/') }
                    i++
                }
                val nameStart = i
                while (i < xml.length && xml[i] !in " \t\n\r>/") i++
                withStyle(SpanStyle(color = colors.key)) { append(xml.substring(nameStart, i)) }
                while (i < xml.length && xml[i] != '>' && !(xml[i] == '/' && i + 1 < xml.length && xml[i + 1] == '>')) {
                    if (xml[i].isWhitespace()) { append(xml[i++]) }
                    else {
                        val attrStart = i
                        while (i < xml.length && xml[i] !in "= \t\n\r>/") i++
                        withStyle(SpanStyle(color = colors.string)) { append(xml.substring(attrStart, i)) }
                        if (i < xml.length && xml[i] == '=') {
                            withStyle(SpanStyle(color = colors.punctuation)) { append('=') }
                            i++
                            if (i < xml.length && xml[i] == '"') {
                                val q = i++
                                while (i < xml.length && xml[i] != '"') i++
                                if (i < xml.length) i++
                                withStyle(SpanStyle(color = colors.number)) { append(xml.substring(q, i)) }
                            }
                        } else if (i == attrStart) {
                            // A slash that closes nothing, as in href=/path: step over it
                            withStyle(SpanStyle(color = colors.punctuation)) { append(xml[i]) }
                            i++
                        }
                    }
                }
                if (i < xml.length && xml[i] == '/') { withStyle(SpanStyle(color = colors.punctuation)) { append('/') }; i++ }
                if (i < xml.length && xml[i] == '>') { withStyle(SpanStyle(color = colors.punctuation)) { append('>') }; i++ }
            }
            else -> {
                val start = i
                while (i < xml.length && xml[i] != '<') i++
                withStyle(SpanStyle(color = colors.plain)) { append(xml.substring(start, i)) }
            }
        }
    }
}

/**
 * This JSON on one line per value, or this text unchanged when it is malformed or when printing it
 * would outgrow [MAX_PRETTY_CHARS]: the indent of a body that only opens brackets grows by the line.
 */
private fun String.prettyPrintJson(): String {
    try {
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
            if (sb.length > MAX_PRETTY_CHARS) return this
            i++
        }
        return sb.toString()
    } catch (_: Exception) {
        return this
    }
}
