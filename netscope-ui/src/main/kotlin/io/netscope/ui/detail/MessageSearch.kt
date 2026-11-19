package io.netscope.ui.detail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import io.netscope.model.NetHeader

/** Most matches a search counts: a one-letter query on a large body would otherwise list millions. */
internal const val MAX_SEARCH_MATCHES = 5_000

/** Where a search query was found in a request or a response. */
internal sealed interface SearchMatch {
    /** In the name or the value of the header at [header]. */
    data class InHeader(val header: Int, val inValue: Boolean, val start: Int) : SearchMatch

    /** On the body line at [line], from the character [start]. */
    data class InBody(val line: Int, val start: Int) : SearchMatch
}

/**
 * Every place [query] is found, case-insensitively, in [headers] then in the body [lines], in reading order and
 * at most [MAX_SEARCH_MATCHES]. A body line is at most [MAX_LINE_CHARS] long, so a match cut by a line break
 * is not found. A large body takes long: call it off the main thread.
 */
internal fun findMatches(headers: List<NetHeader>, lines: List<String>, query: String): List<SearchMatch> {
    if (query.isEmpty()) return emptyList()
    val matches = ArrayList<SearchMatch>()
    headers.forEachIndexed { index, header ->
        header.name.matchStarts(query = query) { start ->
            matches.add(SearchMatch.InHeader(header = index, inValue = false, start = start))
        }
        header.value.matchStarts(query = query) { start ->
            matches.add(SearchMatch.InHeader(header = index, inValue = true, start = start))
        }
        if (matches.size >= MAX_SEARCH_MATCHES) return matches.subList(0, MAX_SEARCH_MATCHES)
    }
    lines.forEachIndexed { index, line ->
        line.matchStarts(query = query) { start -> matches.add(SearchMatch.InBody(line = index, start = start)) }
        if (matches.size >= MAX_SEARCH_MATCHES) return matches.subList(0, MAX_SEARCH_MATCHES)
    }
    return matches
}

/** The colours of the matches of a search. */
internal data class SearchColors(
    val match: Color,
    val current: Color,
    val onCurrent: Color,
)

/**
 * This text with every match of [query] marked, the one starting at [currentStart] more strongly. Search the same
 * text [findMatches] did, or the marks do not line up with the matches it counted.
 */
internal fun AnnotatedString.withMatches(query: String, currentStart: Int?, colors: SearchColors): AnnotatedString {
    if (query.isEmpty()) return this
    val starts = buildList { text.matchStarts(query = query) { add(it) } }
    if (starts.isEmpty()) return this
    return buildAnnotatedString {
        append(this@withMatches)
        starts.forEach { start ->
            val style = if (start == currentStart) {
                SpanStyle(background = colors.current, color = colors.onCurrent)
            } else {
                SpanStyle(background = colors.match)
            }
            addStyle(style = style, start = start, end = start + query.length)
        }
    }
}

private inline fun String.matchStarts(query: String, onMatch: (Int) -> Unit) {
    var from = 0
    while (true) {
        val start = indexOf(query, startIndex = from, ignoreCase = true)
        if (start < 0) return
        onMatch(start)
        from = start + query.length
    }
}
