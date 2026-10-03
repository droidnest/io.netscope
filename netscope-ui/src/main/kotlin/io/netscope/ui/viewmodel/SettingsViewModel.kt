package io.netscope.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.netscope.NetScope
import io.netscope.NetScopeConfig
import io.netscope.NetScopeSettings
import io.netscope.NotificationStyle
import io.netscope.ui.preferences.NetScopePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ── State ─────────────────────────────────────────────────────────────────────

@Immutable
data class SettingsUiState(
    val maxTransactions: Int,
    val maxBodySizeBytes: Long,
    val alwaysReadResponseBody: Boolean,
    val redactedHeaders: Set<String>,
    val shakeToOpen: Boolean,
    val notificationStyle: NotificationStyle,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * Owns the Settings screen state.
 *
 * Every change is persisted first ([NetScopeSettings] for capture & privacy — re-applied by core on
 * every launch — and [NetScopePreferences] for interface settings), then pushed to the running
 * [NetScope] instance through the injected seams so it takes effect immediately.
 *
 * Theme is not handled here: the host activity owns and persists it via `onThemeChange`.
 */
class SettingsViewModel(
    private val netScopeSettings: NetScopeSettings,
    private val netScopePreferences: NetScopePreferences,
    private val configUpdater: (NetScopeConfig.() -> NetScopeConfig) -> Unit = NetScope::updateConfig,
    private val shakeEnabledUpdater: (Boolean) -> Unit = NetScope::setShakeEnabled,
    private val notificationStyleUpdater: (NotificationStyle) -> Unit = { NetScope.notificationStyle = it },
    activeConfigProvider: () -> NetScopeConfig? = { if (NetScope.isInitialized()) NetScope.config else null },
    activeNotificationStyleProvider: () -> NotificationStyle = { NetScope.notificationStyle },
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState(activeConfigProvider(), activeNotificationStyleProvider()))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // ── Capture ───────────────────────────────────────────────────────────────

    fun onMaxTransactionsSelected(value: Int) {
        netScopeSettings.maxTransactions = value
        configUpdater { copy(maxTransactions = value) }
        _uiState.update { it.copy(maxTransactions = value) }
    }

    fun onMaxBodySizeSelected(bytes: Long) {
        netScopeSettings.maxBodySizeBytes = bytes
        configUpdater { copy(maxBodySize = bytes) }
        _uiState.update { it.copy(maxBodySizeBytes = bytes) }
    }

    fun onAlwaysReadResponseBodyChanged(enabled: Boolean) {
        netScopeSettings.alwaysReadResponseBody = enabled
        configUpdater { copy(alwaysReadResponseBody = enabled) }
        _uiState.update { it.copy(alwaysReadResponseBody = enabled) }
    }

    // ── Privacy ───────────────────────────────────────────────────────────────

    /** Adds [header] to the redaction rules. Blank or already-present (case-insensitive) names are ignored. */
    fun onAddRedactedHeader(header: String) {
        val normalized = header.trim().lowercase()
        if (normalized.isEmpty()) return
        val current = _uiState.value.redactedHeaders
        if (current.any { it.equals(normalized, ignoreCase = true) }) return

        val updated = current + normalized
        netScopeSettings.redactedHeaders = updated
        // Interceptor matches on lower-cased header names.
        configUpdater { copy(redactedHeaders = redactedHeaders + normalized) }
        _uiState.update { it.copy(redactedHeaders = updated) }
    }

    /**
     * Removes [header] from the persisted rules. The running config is intentionally **not**
     * loosened: the header may also be declared in code, and core re-applies the union of
     * code-declared and persisted headers on the next launch.
     */
    fun onRemoveRedactedHeader(header: String) {
        val updated = _uiState.value.redactedHeaders - header
        netScopeSettings.redactedHeaders = updated
        _uiState.update { it.copy(redactedHeaders = updated) }
    }

    // ── Interface ─────────────────────────────────────────────────────────────

    fun onShakeToOpenChanged(enabled: Boolean) {
        netScopePreferences.shakeToOpen = enabled
        shakeEnabledUpdater(enabled)
        _uiState.update { it.copy(shakeToOpen = enabled) }
    }

    fun onNotificationStyleSelected(style: NotificationStyle) {
        netScopePreferences.notificationStyle = style
        notificationStyleUpdater(style)
        _uiState.update { it.copy(notificationStyle = style) }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Capture values come from the running config when available (it already includes persisted overrides).
     * The notification style is read from the runtime, never pushed to it: core's `init` already restores a
     * style saved here, and otherwise the host's in-code choice must win — prefs and runtime are only written
     * when the user picks a style.
     */
    private fun initialState(activeConfig: NetScopeConfig?, activeNotificationStyle: NotificationStyle) = SettingsUiState(
        maxTransactions        = activeConfig?.maxTransactions ?: netScopeSettings.maxTransactions,
        maxBodySizeBytes       = activeConfig?.maxBodySize ?: netScopeSettings.maxBodySizeBytes,
        alwaysReadResponseBody = activeConfig?.alwaysReadResponseBody ?: netScopeSettings.alwaysReadResponseBody,
        redactedHeaders        = netScopeSettings.redactedHeaders,
        shakeToOpen            = netScopePreferences.shakeToOpen,
        notificationStyle      = activeNotificationStyle,
    )

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(
            netScopeSettings    = NetScopeSettings(appContext),
            netScopePreferences = NetScopePreferences(appContext),
        ) as T
    }
}
