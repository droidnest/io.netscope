package io.netscope.ui.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.netscope.NetScopeConfig
import io.netscope.NetScopeSettings
import io.netscope.NotificationStyle
import io.netscope.ui.preferences.NetScopePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelTest {

    private lateinit var context: Context
    private lateinit var netScopeSettings: NetScopeSettings
    private lateinit var netScopePreferences: NetScopePreferences

    /** Stands in for NetScope's running config; the VM's updater applies transforms to it. */
    private var liveConfig = NetScopeConfig(redactedHeaders = setOf("authorization"))
    private var shakeEnabled: Boolean? = null
    private var notificationStyle: NotificationStyle? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("netscope_settings", Context.MODE_PRIVATE).edit().clear().commit()
        netScopeSettings    = NetScopeSettings(context)
        netScopePreferences = NetScopePreferences(context)
    }

    private fun createViewModel(activeConfig: NetScopeConfig? = null) = SettingsViewModel(
        netScopeSettings         = netScopeSettings,
        netScopePreferences      = netScopePreferences,
        configUpdater            = { transform -> liveConfig = liveConfig.transform() },
        shakeEnabledUpdater      = { shakeEnabled = it },
        notificationStyleUpdater = { notificationStyle = it },
        activeConfigProvider     = { activeConfig },
    )

    // ── Privacy ───────────────────────────────────────────────────────────────

    @Test
    fun `adding redacted header persists it and pushes lower-cased name to config`() {
        // Given
        val viewModel = createViewModel()

        // When
        viewModel.onAddRedactedHeader("  X-Api-Key ")

        // Then
        assertTrue("x-api-key" in NetScopeSettings(context).redactedHeaders)
        assertTrue("x-api-key" in liveConfig.redactedHeaders)
        assertFalse("X-Api-Key" in liveConfig.redactedHeaders)
        assertTrue("authorization" in liveConfig.redactedHeaders)
        assertTrue("x-api-key" in viewModel.uiState.value.redactedHeaders)
    }

    @Test
    fun `adding blank or duplicate header is ignored`() {
        // Given
        val viewModel = createViewModel()
        val before = viewModel.uiState.value.redactedHeaders

        // When
        viewModel.onAddRedactedHeader("   ")
        viewModel.onAddRedactedHeader("AUTHORIZATION")

        // Then
        assertEquals(before, viewModel.uiState.value.redactedHeaders)
    }

    @Test
    fun `removing redacted header persists removal without loosening live config`() {
        // Given
        val viewModel = createViewModel()

        // When
        viewModel.onRemoveRedactedHeader("Authorization")

        // Then
        assertFalse("Authorization" in NetScopeSettings(context).redactedHeaders)
        assertFalse("Authorization" in viewModel.uiState.value.redactedHeaders)
        assertTrue("authorization" in liveConfig.redactedHeaders)
    }

    // ── Capture ───────────────────────────────────────────────────────────────

    @Test
    fun `changing max transactions persists and updates config`() {
        // Given
        val viewModel = createViewModel()

        // When
        viewModel.onMaxTransactionsSelected(2_000)

        // Then
        assertEquals(2_000, NetScopeSettings(context).maxTransactions)
        assertEquals(2_000, liveConfig.maxTransactions)
        assertEquals(2_000, viewModel.uiState.value.maxTransactions)
    }

    @Test
    fun `changing body size and always-read persists and updates config`() {
        // Given
        val viewModel = createViewModel()

        // When
        viewModel.onMaxBodySizeSelected(1_048_576L)
        viewModel.onAlwaysReadResponseBodyChanged(false)

        // Then
        assertEquals(1_048_576L, NetScopeSettings(context).maxBodySizeBytes)
        assertFalse(NetScopeSettings(context).alwaysReadResponseBody)
        assertEquals(1_048_576L, liveConfig.maxBodySize)
        assertFalse(liveConfig.alwaysReadResponseBody)
    }

    // ── Interface ─────────────────────────────────────────────────────────────

    @Test
    fun `changing interface settings persists and pushes to runtime`() {
        // Given
        val viewModel = createViewModel()

        // When
        viewModel.onShakeToOpenChanged(false)
        viewModel.onNotificationStyleSelected(NotificationStyle.MINIMAL)

        // Then
        assertFalse(NetScopePreferences(context).shakeToOpen)
        assertEquals(NotificationStyle.MINIMAL, NetScopePreferences(context).notificationStyle)
        assertEquals(false, shakeEnabled)
        assertEquals(NotificationStyle.MINIMAL, notificationStyle)
    }

    // ── Restore ───────────────────────────────────────────────────────────────

    @Test
    fun `fresh view model reflects previously persisted values`() {
        // Given
        createViewModel().apply {
            onMaxTransactionsSelected(1_000)
            onMaxBodySizeSelected(50_000L)
            onAlwaysReadResponseBodyChanged(false)
            onAddRedactedHeader("X-Token")
            onShakeToOpenChanged(false)
            onNotificationStyleSelected(NotificationStyle.NONE)
        }
        notificationStyle = null

        // When
        val state = SettingsViewModel(
            netScopeSettings         = NetScopeSettings(context),
            netScopePreferences      = NetScopePreferences(context),
            configUpdater            = {},
            shakeEnabledUpdater      = {},
            notificationStyleUpdater = { notificationStyle = it },
            activeConfigProvider     = { null },
        ).uiState.value

        // Then
        assertEquals(1_000, state.maxTransactions)
        assertEquals(50_000L, state.maxBodySizeBytes)
        assertFalse(state.alwaysReadResponseBody)
        assertTrue("x-token" in state.redactedHeaders)
        assertFalse(state.shakeToOpen)
        assertEquals(NotificationStyle.NONE, state.notificationStyle)
        // Persisted notification style is re-applied to the runtime.
        assertEquals(NotificationStyle.NONE, notificationStyle)
    }

    @Test
    fun `capture values come from running config when available`() {
        // Given — host declared maxTransactions(200) in code; user never changed it in Settings.
        val running = NetScopeConfig(maxTransactions = 200)

        // When
        val state = createViewModel(activeConfig = running).uiState.value

        // Then
        assertEquals(200, state.maxTransactions)
    }
}
