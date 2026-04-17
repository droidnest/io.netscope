package io.netscope

import android.content.Context
import io.netscope.data.db.NetScopeDatabase
import io.netscope.data.repository.TransactionRepositoryImpl
import io.netscope.domain.repository.TransactionRepository
import io.netscope.interceptor.NetScopeInterceptor
import io.netscope.interceptor.NetScopeTimingListenerFactory
import io.netscope.shake.NetScopeShakeDetector

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

    lateinit var repository: TransactionRepository
        private set

    // Active config — kept in sync with the running interceptor and repository.
    private lateinit var _config: NetScopeConfig
    val config: NetScopeConfig get() = _config

    private var initialized = false

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
        return NetScopeInterceptor(repository, config).also { activeInterceptor = it }
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


    private fun init(context: Context, config: NetScopeConfig) {
        val db = NetScopeDatabase.getInstance(context)
        _config = config
        repository = TransactionRepositoryImpl(db.transactionDao(), config)
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
