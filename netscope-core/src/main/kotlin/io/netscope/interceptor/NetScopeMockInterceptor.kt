package io.netscope.interceptor

import io.netscope.NetScope
import io.netscope.model.MockRule
import okhttp3.Headers
import okhttp3.Headers.Companion.headersOf
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * OkHttp interceptor that short-circuits matching requests and returns a fixture response
 * defined by the first active [io.netscope.model.MockRule] whose pattern matches the URL.
 *
 * Add **after** [NetScopeInterceptor] so mocked requests are still recorded in the
 * transaction list with the fixture response. It is also the order that works at startup:
 * [NetScope.mockInterceptor] needs [NetScope.interceptor] to have run.
 *
 * ```kotlin
 * OkHttpClient.Builder()
 *     .addInterceptor(NetScope.interceptor(context))
 *     .addInterceptor(NetScope.mockInterceptor())   // ← mock second
 *     .build()
 * ```
 */
class NetScopeMockInterceptor internal constructor(
    private val rules: () -> List<MockRule>,
) : Interceptor {

    constructor() : this(rules = { NetScope.mockRules.value })

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url     = request.url.toString()

        val rule = rules()
            .firstOrNull { it.isEnabled && it.matches(url, request.method) }
            ?: return chain.proceed(request)

        if (rule.delayMs > 0) Thread.sleep(rule.delayMs)

        val body = rule.responseBody
            .toResponseBody(rule.contentType.toMediaTypeOrNull())

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(rule.statusCode)
            .message(httpStatusMessage(rule.statusCode))
            .headers(contentTypeHeaders(rule.contentType))
            .body(body)
            .build()
    }

    /** OkHttp rejects a value it cannot send, which would crash every matching request. */
    private fun contentTypeHeaders(contentType: String): Headers =
        runCatching { headersOf("Content-Type", contentType) }.getOrDefault(headersOf())

    private fun httpStatusMessage(code: Int): String = when (code) {
        200  -> "OK"
        201  -> "Created"
        204  -> "No Content"
        301  -> "Moved Permanently"
        302  -> "Found"
        304  -> "Not Modified"
        400  -> "Bad Request"
        401  -> "Unauthorized"
        403  -> "Forbidden"
        404  -> "Not Found"
        408  -> "Request Timeout"
        409  -> "Conflict"
        422  -> "Unprocessable Entity"
        429  -> "Too Many Requests"
        500  -> "Internal Server Error"
        502  -> "Bad Gateway"
        503  -> "Service Unavailable"
        504  -> "Gateway Timeout"
        else -> "Unknown"
    }
}
