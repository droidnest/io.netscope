package io.netscope

import android.content.Context
import io.netscope.domain.repository.MockRuleRepository
import io.netscope.domain.repository.SettingsRepository
import io.netscope.domain.repository.TransactionRepository
import io.netscope.model.MockRule
import io.netscope.model.NetTransaction
import io.netscope.model.SettingChange
import io.netscope.model.Settings
import io.netscope.model.TrafficSummary
import io.netscope.model.TransactionSummary
import io.netscope.shake.NetScopeShakeDetector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Release no-op implementation of NetScope.
 *
 * All calls are empty stubs. The interceptor passes through requests untouched.
 * This artifact has no transitive dependencies and adds ~2KB to your APK. The members typed with
 * `Flow` and `StateFlow` need kotlinx-coroutines in the app, like any code that collects them.
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

    /** No-op: returns a pass-through interceptor; no request is ever mocked. */
    fun mockInterceptor(): Interceptor = NoOpInterceptor()

    fun isInitialized(): Boolean = false

    // ── Config ────────────────────────────────────────────────────────────────

    val config: NetScopeConfig = NetScopeConfig()
    fun updateConfig(transform: NetScopeConfig.() -> NetScopeConfig) = Unit

    // ── Repositories ──────────────────────────────────────────────────────────

    /** No-op: stores nothing and is always empty. */
    val repository: TransactionRepository get() = NoOpTransactionRepository

    /** No-op: reports the defaults and ignores every change. */
    val settingsRepository: SettingsRepository get() = NoOpSettingsRepository

    /** No-op: holds no rule. */
    val mockRuleRepository: MockRuleRepository get() = NoOpMockRuleRepository

    // ── Mock rules ────────────────────────────────────────────────────────────

    /** No-op: always empty. */
    val mockRules: StateFlow<List<MockRule>> get() = NoOpMockRuleRepository.rules

    fun addMockRule(rule: MockRule) = Unit
    fun updateMockRule(rule: MockRule) = Unit
    fun deleteMockRule(id: String) = Unit
    fun toggleMockRule(id: String) = Unit

    // ── Shake detector ────────────────────────────────────────────────────────

    val shakeEnabled: Boolean = false
    fun registerShakeDetector(detector: NetScopeShakeDetector) = Unit
    fun setShakeEnabled(enabled: Boolean) = Unit

    // ── Notification style ────────────────────────────────────────────────────

    var notificationStyle: NotificationStyle = NotificationStyle.DETAILED

    /** No-op: emits the default style and no change. */
    val notificationStyleFlow: StateFlow<NotificationStyle> by lazy {
        MutableStateFlow(NotificationStyle.DETAILED)
    }

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

    private object NoOpTransactionRepository : TransactionRepository {
        override suspend fun insert(transaction: NetTransaction): Long = 0L
        override suspend fun update(transaction: NetTransaction) = Unit
        override fun observeAll(): Flow<List<NetTransaction>> = flowOf(emptyList())
        override fun observeSummaries(query: String): Flow<List<TransactionSummary>> = flowOf(emptyList())
        override fun observeSummary(): Flow<TrafficSummary> = flowOf(TrafficSummary(0, 0, null))
        override suspend fun getById(id: Long): NetTransaction? = null
        override suspend fun getByIds(ids: List<Long>): List<NetTransaction> = emptyList()
        override fun observeById(id: Long): Flow<NetTransaction?> = flowOf(null)
        override suspend fun updateTiming(id: Long, dnsMs: Long?, connectMs: Long?, sslMs: Long?, ttfbMs: Long?) = Unit
        override suspend fun clearAll() = Unit
    }

    private object NoOpSettingsRepository : SettingsRepository {
        override fun getSettings(): Settings = Settings(
            maxTransactions = config.maxTransactions,
            maxBodySize = config.maxBodySize,
            alwaysReadResponseBody = config.alwaysReadResponseBody,
            redactedHeaders = config.redactedHeaders,
            shakeToOpen = shakeEnabled,
            notificationStyle = notificationStyle,
        )

        override fun update(change: SettingChange) = Unit
    }

    private object NoOpMockRuleRepository : MockRuleRepository {
        override val rules: StateFlow<List<MockRule>> by lazy { MutableStateFlow(emptyList()) }
        override fun getById(id: String): MockRule? = null
        override fun add(rule: MockRule) = Unit
        override fun update(rule: MockRule) = Unit
        override fun delete(id: String) = Unit
        override fun toggle(id: String) = Unit
    }
}
