package io.netscope

import android.content.Context
import io.netscope.data.db.NetScopeDatabase
import io.netscope.data.local.SavedSettings
import io.netscope.data.repository.MockRuleRepositoryImpl
import io.netscope.data.repository.SettingsRepositoryImpl
import io.netscope.data.repository.TransactionRepositoryImpl
import io.netscope.domain.repository.MockRuleRepository
import io.netscope.domain.repository.SettingsRepository
import io.netscope.domain.repository.TransactionRepository
import io.netscope.interceptor.NetScopeMockInterceptor
import io.netscope.interceptor.NetScopeInterceptor
import io.netscope.interceptor.NetScopeTimingListenerFactory
import io.netscope.model.MockRule
import io.netscope.shake.NetScopeShakeDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    /** The captured transactions. Read by the NetScope UI and notification; available after [interceptor]. */
    lateinit var repository: TransactionRepository
        private set

    /** The saved and running settings. Read by the NetScope UI; available after [interceptor]. */
    lateinit var settingsRepository: SettingsRepository
        private set

    // ── Mock rules ────────────────────────────────────────────────────────────

    private var mockRuleStore: MockRuleRepository? = null
    private val _emptyRules = MutableStateFlow<List<MockRule>>(emptyList())

    /** The configured mock rules. Read by the NetScope UI; available after [interceptor]. */
    val mockRuleRepository: MockRuleRepository
        get() = checkNotNull(mockRuleStore) { "Call NetScope.interceptor(context) first." }

    /** Live list of all configured mock rules. Observe in the UI via a ViewModel. */
    val mockRules: StateFlow<List<MockRule>>
        get() = mockRuleStore?.rules ?: _emptyRules

    /** Appends [rule]; ignored before [interceptor] is called. */
    fun addMockRule(rule: MockRule)      = mockRuleStore?.add(rule)
    /** Replaces the rule with the same id; ignored before [interceptor] is called. */
    fun updateMockRule(rule: MockRule)   = mockRuleStore?.update(rule)
    /** Removes the rule with this [id]; ignored before [interceptor] is called. */
    fun deleteMockRule(id: String)       = mockRuleStore?.delete(id)
    /** Enables or disables the rule with this [id]; ignored before [interceptor] is called. */
    fun toggleMockRule(id: String)       = mockRuleStore?.toggle(id)

    // Active config — kept in sync with the running interceptor and repository.
    private lateinit var _config: NetScopeConfig

    /** The configuration the library runs with; available after [interceptor]. */
    val config: NetScopeConfig get() = _config

    @Volatile private var initialized = false

    @Volatile private var activeInterceptor: NetScopeInterceptor? = null

    // ── Notification style ────────────────────────────────────────────────────

    private val _notificationStyleFlow = MutableStateFlow(NotificationStyle.DETAILED)

    /** Emits [notificationStyle] and every change of it. */
    val notificationStyleFlow: StateFlow<NotificationStyle> = _notificationStyleFlow.asStateFlow()

    /** Controls notification display density. Updated immediately by the Settings UI. */
    var notificationStyle: NotificationStyle
        get() = _notificationStyleFlow.value
        set(value) {
            _notificationStyleFlow.value = value
        }

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
     *
     * Values the user saved in the NetScope Settings screen override the ones set in [block];
     * saved redacted headers are added to the configured ones.
     */
    fun interceptor(
        context: Context,
        block: ConfigBuilder.() -> Unit = {},
    ): NetScopeInterceptor {
        val config = ConfigBuilder().apply(block).build()
        init(context.applicationContext, config)
        return NetScopeInterceptor(repository, _config).also { activeInterceptor = it }
    }

    /**
     * Creates a [NetScopeMockInterceptor] that short-circuits matching requests.
     * Add it **after** [interceptor] in the OkHttpClient chain: the capture has to run first
     * for mocked responses to be recorded in the transaction list.
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
        val newConfig = _config.transform().withNormalizedHeaders()
        _config = newConfig
        activeInterceptor?.config = newConfig
        (repository as? TransactionRepositoryImpl)?.config = newConfig
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    @Synchronized
    private fun init(context: Context, config: NetScopeConfig) {
        val db = NetScopeDatabase.getInstance(context)
        val savedSettings = SavedSettings(context)
        _config = savedSettings.applyTo(config)
        repository = TransactionRepositoryImpl(db.transactionDao(), _config)
        settingsRepository = SettingsRepositoryImpl(
            savedSettings = savedSettings,
            configuredRedactedHeaders = config.redactedHeaders,
        )
        savedSettings.notificationStyle?.let { notificationStyle = it }
        savedSettings.shakeToOpen?.let { shakeEnabled = it }
        if (mockRuleStore == null) mockRuleStore = MockRuleRepositoryImpl(context)
        initialized = true
    }

    /** Returns true if [interceptor] has been called at least once. */
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
