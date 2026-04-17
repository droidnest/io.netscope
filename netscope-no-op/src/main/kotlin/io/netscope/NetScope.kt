package io.netscope

import android.content.Context
import io.netscope.shake.NetScopeShakeDetector
import okhttp3.Call
import okhttp3.EventListener
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

    /** No-op: returns a pass-through [EventListener.Factory]. */
    fun timingListenerFactory(): EventListener.Factory =
        EventListener.Factory { EventListener.NONE }

    fun isInitialized(): Boolean = false

    // ── Shake detector ────────────────────────────────────────────────────────

    val shakeEnabled: Boolean = false
    fun registerShakeDetector(detector: NetScopeShakeDetector) = Unit
    fun setShakeEnabled(enabled: Boolean) = Unit

    // ── Notification style ────────────────────────────────────────────────────

    var notificationStyle: NotificationStyle = NotificationStyle.DETAILED

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
