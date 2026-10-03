package io.netscope

import android.content.Context

/**
 * No-op mirror of core's `NetScopeSettings` so host code compiles against the release artifact.
 * Nothing is persisted: getters return the defaults and setters are ignored.
 */
@Suppress("UNUSED_PARAMETER")
class NetScopeSettings(context: Context) {

    var maxTransactions: Int
        get() = DEFAULT_MAX_TX
        set(v) = Unit

    var maxBodySizeBytes: Long
        get() = DEFAULT_MAX_BODY
        set(v) = Unit

    var alwaysReadResponseBody: Boolean
        get() = true
        set(v) = Unit

    var redactedHeaders: Set<String>
        get() = DEFAULT_REDACTED
        set(v) = Unit

    companion object {
        const val DEFAULT_MAX_TX   = 500
        const val DEFAULT_MAX_BODY = 250_000L
        val DEFAULT_REDACTED: Set<String> = setOf("Authorization", "Cookie", "Set-Cookie")
    }
}
