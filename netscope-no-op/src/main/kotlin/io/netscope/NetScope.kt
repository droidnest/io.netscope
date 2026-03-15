package io.netscope

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Release no-op implementation of NetScope.
 *
 * All calls are empty stubs. The interceptor passes through requests untouched.
 * This artifact has no transitive dependencies and adds ~2KB to your APK.
 *
 * Usage in build.gradle:
 * ```
 * debugImplementation("io.netscope:netscope-core:1.0.0")
 * debugImplementation("io.netscope:netscope-ui:1.0.0")
 * releaseImplementation("io.netscope:netscope-no-op:1.0.0")
 * ```
 */
object NetScope {

    fun interceptor(
        context: Context,
        block: ConfigBuilder.() -> Unit = {},
    ): Interceptor = NoOpInterceptor()

    fun isInitialized(): Boolean = false

    class ConfigBuilder {
        fun maxTransactions(count: Int) = this
        fun redactHeaders(vararg headers: String) = this
        fun maxBodySize(bytes: Long) = this
        fun showNotification(show: Boolean) = this
        fun alwaysReadResponseBody(always: Boolean) = this
    }

    private class NoOpInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
    }
}
