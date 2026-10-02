package io.netscope

import android.content.Context
import io.netscope.model.MockRule
import io.netscope.shake.NetScopeShakeDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Release no-op implementation of NetScope.
 *
 * Mirrors the public API of the `netscope-core` [NetScope] object so that host code
 * compiling against core in debug also compiles against this artifact in release.
 * Nothing is captured, stored, or mocked: both interceptors pass requests through
 * untouched and every mutator is an empty stub.
 *
 * Differences from core (source-compatible for typical `addInterceptor(...)` usage):
 * - [interceptor] / [mockInterceptor] return a plain OkHttp [Interceptor] and
 *   [timingListenerFactory] a plain [EventListener.Factory] instead of NetScope's own types.
 * - `NetScope.repository` is not exposed — it is an inspector-UI implementation detail.
 *
 * Transitive dependency: `kotlinx-coroutines-core` (needed for the [mockRules] [StateFlow]).
 *
 * Usage in build.gradle:
 * ```
 * debugImplementation("io.github.droidnest:netscope-core:<version>")
 * debugImplementation("io.github.droidnest:netscope-ui:<version>")
 * releaseImplementation("io.github.droidnest:netscope-no-op:<version>")
 * ```
 */
object NetScope {

    // ── Mock rules ────────────────────────────────────────────────────────────

    private val emptyRules = MutableStateFlow<List<MockRule>>(emptyList()).asStateFlow()

    /** No-op: always an empty list. */
    val mockRules: StateFlow<List<MockRule>> get() = emptyRules

    fun addMockRule(rule: MockRule): Unit? = null
    fun updateMockRule(rule: MockRule): Unit? = null
    fun deleteMockRule(id: String): Unit? = null
    fun toggleMockRule(id: String): Unit? = null

    // ── Config ────────────────────────────────────────────────────────────────

    /** No-op: always the default [NetScopeConfig]. */
    val config: NetScopeConfig = NetScopeConfig()

    /** No-op: the transform is never applied. */
    fun updateConfig(transform: NetScopeConfig.() -> NetScopeConfig) = Unit

    // ── Notification style ────────────────────────────────────────────────────

    @Volatile var notificationStyle: NotificationStyle = NotificationStyle.DETAILED

    // ── Shake-to-open ─────────────────────────────────────────────────────────

    /** Always `false` in release. */
    @Volatile var shakeEnabled: Boolean = false
        private set

    fun registerShakeDetector(detector: NetScopeShakeDetector) = Unit
    fun setShakeEnabled(enabled: Boolean) = Unit

    // ── Interceptors ──────────────────────────────────────────────────────────

    /** No-op: returns a pass-through [Interceptor]. */
    fun interceptor(
        context: Context,
        block: ConfigBuilder.() -> Unit = {},
    ): Interceptor = PassThroughInterceptor

    /** No-op: returns a pass-through [Interceptor] — no rule ever matches. */
    fun mockInterceptor(): Interceptor = PassThroughInterceptor

    /** No-op: returns a pass-through [EventListener.Factory]. */
    fun timingListenerFactory(): EventListener.Factory =
        EventListener.Factory { EventListener.NONE }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun ensureInitialized(context: Context) = Unit

    /** Always `false`: nothing is ever initialised in release. */
    fun isInitialized(): Boolean = false

    // ── DSL Builder ───────────────────────────────────────────────────────────

    class ConfigBuilder {
        fun maxTransactions(count: Int) = this
        fun redactHeaders(vararg headers: String) = this
        fun maxBodySize(bytes: Long) = this
        fun showNotification(show: Boolean) = this
        fun alwaysReadResponseBody(always: Boolean) = this
    }

    private object PassThroughInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
    }
}
