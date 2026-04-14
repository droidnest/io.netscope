package io.netscope

import android.content.Context
import io.netscope.data.db.NetScopeDatabase
import io.netscope.data.repository.TransactionRepositoryImpl
import io.netscope.domain.repository.TransactionRepository
import io.netscope.interceptor.NetScopeInterceptor
import io.netscope.interceptor.NetScopeTimingListenerFactory

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
 *             showNotification(true)
 *         }
 *     )
 *     .build()
 * ```
 */
object NetScope {

    lateinit var repository: TransactionRepository
        private set

    private var initialized = false

    @Volatile
    private var activeInterceptor: NetScopeInterceptor? = null

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
     * Add this to your `OkHttpClient` alongside [interceptor]:
     * ```kotlin
     * val timingFactory = NetScope.timingListenerFactory()
     * OkHttpClient.Builder()
     *     .addInterceptor(NetScope.interceptor(context) { … })
     *     .eventListenerFactory(timingFactory)
     *     .build()
     * ```
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

    private fun init(context: Context, config: NetScopeConfig) {
        val db = NetScopeDatabase.getInstance(context)
        repository = TransactionRepositoryImpl(db.transactionDao(), config)
        initialized = true
    }

    /** Returns true if [interceptor] has been called at least once. */
    fun isInitialized(): Boolean = initialized

    // ─── DSL Builder ──────────────────────────────────────────────────────────

    class ConfigBuilder {
        private var maxTransactions: Int = 500
        private var redactedHeaders: MutableSet<String> = mutableSetOf("Authorization", "Cookie", "Set-Cookie")
        private var maxBodySize: Long = 250_000L
        private var showNotification: Boolean = true
        private var alwaysReadResponseBody: Boolean = true

        fun maxTransactions(count: Int) = apply { maxTransactions = count }
        fun redactHeaders(vararg headers: String) = apply { redactedHeaders.addAll(headers) }
        fun maxBodySize(bytes: Long) = apply { maxBodySize = bytes }
        fun showNotification(show: Boolean) = apply { showNotification = show }
        fun alwaysReadResponseBody(always: Boolean) = apply { alwaysReadResponseBody = always }

        internal fun build() = NetScopeConfig(
            maxTransactions = maxTransactions,
            redactedHeaders = redactedHeaders.map { it.lowercase() }.toSet(),
            maxBodySize = maxBodySize,
            showNotification = showNotification,
            alwaysReadResponseBody = alwaysReadResponseBody,
        )
    }
}
