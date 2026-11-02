package io.netscope.model

/** Ordering applied to the filtered transaction list. */
enum class SortOrder {
    /** Newest first, as stored. */
    TIME,

    /** Slowest first; pending requests last. */
    DURATION,

    /** Largest request + response body first. */
    SIZE,
}
