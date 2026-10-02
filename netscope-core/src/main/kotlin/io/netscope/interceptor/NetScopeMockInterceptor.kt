package io.netscope.interceptor

import io.netscope.NetScope
import io.netscope.model.MockRule
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * OkHttp interceptor that short-circuits matching requests and returns a fixture response
 * defined by the first active [io.netscope.model.MockRule] whose pattern matches the URL.
 *
 * Application interceptors run in the order they are added, and this one never calls
 * `proceed()` for a matching request. Add it **after** [NetScopeInterceptor] so the recorder
 * sees the request first and records the fixture response it returns:
 *
 * ```kotlin
 * OkHttpClient.Builder()
 *     .addInterceptor(NetScope.interceptor(context))   // ← recorder first
 *     .addInterceptor(NetScope.mockInterceptor())      // ← then mock
 *     .build()
 * ```
 *
 * Added the other way round, mocked calls never reach [NetScopeInterceptor] and are not recorded.
 */
class NetScopeMockInterceptor internal constructor(
    /** Source of the current rules — injectable so tests don't need the [NetScope] singleton. */
    private val rulesProvider: () -> List<MockRule>,
) : Interceptor {

    constructor() : this({ NetScope.mockRules.value })

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url     = request.url.toString()

        val rule = rulesProvider()
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
            .addHeader("Content-Type", rule.contentType)
            .body(body)
            .build()
    }

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
