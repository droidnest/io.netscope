package io.netscope

import android.content.Context
import io.netscope.data.db.NetScopeDatabase
import io.netscope.data.repository.TransactionRepositoryImpl
import io.netscope.domain.repository.TransactionRepository
import io.netscope.interceptor.NetScopeMockInterceptor
import io.netscope.interceptor.NetScopeInterceptor
import io.netscope.interceptor.NetScopeTimingListenerFactory
import io.netscope.mock.MockRuleStore
import io.netscope.model.MockRule
import io.netscope.shake.NetScopeShakeDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Main entry point for the NetScope library.
 *
 * Usage:
 * ```kotlin
 * OkHttpClient.Builder()
 *     .addInterceptor(
 *         NetScope.interceptor(context) {
 *             maxTransactions(500)
 *             redactHeaders("Authorization", "X-Api-Key")
 *         }
 *     )
 *     .build()
 * ```
 */
object NetScope {

    // Written on the UI thread by init(), read on OkHttp threads — hence @Volatile.
    @Volatile lateinit var repository: TransactionRepository
        private set

    // ── Mock rules ────────────────────────────────────────────────────────────

    @Volatile private var mockRuleStore: MockRuleStore? = null
    private val _emptyRules = MutableStateFlow<List<MockRule>>(emptyList())

    /** Live list of all configured mock rules. Observe in the UI via a ViewModel. */
    val mockRules: StateFlow<List<MockRule>>
        get() = mockRuleStore?.rules ?: _emptyRules

    fun addMockRule(rule: MockRule)      = mockRuleStore?.add(rule)
    fun updateMockRule(rule: MockRule)   = mockRuleStore?.update(rule)
    fun deleteMockRule(id: String)       = mockRuleStore?.delete(id)
    fun toggleMockRule(id: String)       = mockRuleStore?.toggle(id)

    // Active config — kept in sync with the running interceptor and repository.
    @Volatile private lateinit var _config: NetScopeConfig
    val config: NetScopeConfig get() = _config

    @Volatile private var initialized = false

    @Volatile private var activeInterceptor: NetScopeInterceptor? = null

    // ── Notification style ────────────────────────────────────────────────────

    /** Controls notification display density. Updated immediately by the Settings UI. */
    @Volatile var notificationStyle: NotificationStyle = NotificationStyle.DETAILED

    // ── Shake-to-open ─────────────────────────────────────────────────────────

    /** Whether the shake gesture is currently enabled. */
    @Volatile var shakeEnabled: Boolean = true
        private set

    @Volatile private var shakeDetector: NetScopeShakeDetector? = null

    /**
     * Registers the app's [NetScopeShakeDetector] so that [setShakeEnabled]
     * can start/stop it immediately when the user toggles the setting.
     *
     * Call once after creating your detector, before `onResume`.
     */
    fun registerShakeDetector(detector: NetScopeShakeDetector) {
        shakeDetector = detector
    }

    /**
     * Enables or disables the shake gesture at runtime.
     * Immediately starts or stops the registered [NetScopeShakeDetector].
     */
    fun setShakeEnabled(enabled: Boolean) {
        shakeEnabled = enabled
        if (enabled) shakeDetector?.start() else shakeDetector?.stop()
    }

    // ── Interceptor ───────────────────────────────────────────────────────────

    /**
     * Creates a configured [NetScopeInterceptor] and initialises the library.
     * Safe to call multiple times — subsequent calls reconfigure without data loss.
     */
    fun interceptor(
        context: Context,
        block: ConfigBuilder.() -> Unit = {},
    ): NetScopeInterceptor {
        val config = ConfigBuilder().apply(block).build()
        init(context.applicationContext, config)
        // _config, not config: init() overlays the persisted Settings on top of the DSL values.
        return NetScopeInterceptor(repository, _config).also { activeInterceptor = it }
    }

    /**
     * Creates a [NetScopeMockInterceptor] that short-circuits matching requests.
     * Add it **after** [interceptor] in the OkHttpClient chain — application interceptors
     * run in the order added, so the recorder must come first to see mocked responses:
     *
     * ```kotlin
     * OkHttpClient.Builder()
     *     .addInterceptor(NetScope.interceptor(context))
     *     .addInterceptor(NetScope.mockInterceptor())
     *     .build()
     * ```
     *
     * **Must be called after [interceptor]** so that the library is initialised.
     */
    fun mockInterceptor(): NetScopeMockInterceptor {
        check(isInitialized()) {
            "Call NetScope.interceptor(context) before NetScope.mockInterceptor()."
        }
        return NetScopeMockInterceptor()
    }

    /**
     * Returns an [okhttp3.EventListener.Factory] that captures per-phase network
     * timing (DNS / TCP connect / TLS / TTFB) and writes it to the repository.
     *
     * **Must be called after [interceptor]** so that the repository is initialised.
     */
    fun timingListenerFactory(): NetScopeTimingListenerFactory {
        check(isInitialized()) {
            "Call NetScope.interceptor(context) before NetScope.timingListenerFactory()."
        }
        return NetScopeTimingListenerFactory(repository).also { factory ->
            activeInterceptor?.timingFactory = factory
        }
    }

    // ── Runtime config update ─────────────────────────────────────────────────

    /**
     * Applies a config transformation immediately to the running interceptor and
     * repository — no restart required.
     *
     * Example (from Settings):
     * ```kotlin
     * NetScope.updateConfig { copy(maxTransactions = 1000) }
     * ```
     */
    fun updateConfig(transform: NetScopeConfig.() -> NetScopeConfig) {
        if (!isInitialized()) return
        val newConfig = _config.transform()
        _config = newConfig
        activeInterceptor?.config = newConfig
        (repository as? TransactionRepositoryImpl)?.config = newConfig
    }

    // ── Internal ──────────────────────────────────────────────────────────────


    @Synchronized
    private fun init(context: Context, config: NetScopeConfig) {
        val db = NetScopeDatabase.getInstance(context)
        // Settings can only add redaction on top of what the code declares — never remove it.
        val settings = NetScopeSettings(context)
        val effective = settings.applyTo(config)
        settings.savedNotificationStyle?.let { notificationStyle = it }
        _config = effective
        repository = TransactionRepositoryImpl(db.transactionDao(), effective)
        if (mockRuleStore == null) mockRuleStore = MockRuleStore(context)
        initialized = true
    }

    /**
     * Initialises storage with default capture config if [interceptor] has not run yet.
     * Lets the inspector UI open after process death even when the host builds OkHttp lazily.
     */
    fun ensureInitialized(context: Context) {
        if (!initialized) init(context.applicationContext, ConfigBuilder().build())
    }

    /** Returns true if [interceptor] or [ensureInitialized] has been called at least once. */
    fun isInitialized(): Boolean = initialized

    // ── DSL Builder ───────────────────────────────────────────────────────────

    class ConfigBuilder {
        private var maxTransactions: Int = 500
        private var redactedHeaders: MutableSet<String> = mutableSetOf("Authorization", "Cookie", "Set-Cookie")
        private var maxBodySize: Long = 250_000L
        private var showNotification: Boolean = true
        private var alwaysReadResponseBody: Boolean = true

        fun maxTransactions(count: Int)          = apply { maxTransactions = count }
        fun redactHeaders(vararg headers: String) = apply { redactedHeaders.addAll(headers) }
        fun maxBodySize(bytes: Long)              = apply { maxBodySize = bytes }
        fun showNotification(show: Boolean)       = apply { showNotification = show }
        fun alwaysReadResponseBody(always: Boolean) = apply { alwaysReadResponseBody = always }

        internal fun build() = NetScopeConfig(
            maxTransactions     = maxTransactions,
            redactedHeaders     = redactedHeaders.map { it.lowercase() }.toSet(),
            maxBodySize         = maxBodySize,
            showNotification    = showNotification,
            alwaysReadResponseBody = alwaysReadResponseBody,
        )
    }
}
