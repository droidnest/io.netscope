package io.netscope.ui.settings

import io.netscope.domain.usecase.settings.GetSettingsUseCase
import io.netscope.domain.usecase.settings.UpdateSettingUseCase
import io.netscope.domain.usecase.transaction.ClearTransactionsUseCase
import io.netscope.model.SettingChange
import io.netscope.ui.MainDispatcherRule
import io.netscope.ui.fake.FakeSettingsRepository
import io.netscope.ui.fake.FakeTransactionRepository
import io.netscope.ui.fake.aSettings
import io.netscope.ui.fake.aTransaction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settingsRepository = FakeSettingsRepository()
    private val transactionRepository = FakeTransactionRepository(transactions = listOf(aTransaction()))

    private lateinit var sut: SettingsViewModel

    @Before
    fun setUp() {
        sut = SettingsViewModel(
            getSettingsUseCase = GetSettingsUseCase(repository = settingsRepository),
            updateSettingUseCase = UpdateSettingUseCase(repository = settingsRepository),
            clearTransactionsUseCase = ClearTransactionsUseCase(repository = transactionRepository),
        )
    }

    private val state: SettingsUiState
        get() = sut.uiState.value.dataOrNull()!!

    @Test
    fun `should show what the library runs with when created`() {
        assertEquals(aSettings(), state.content.settings)
    }

    @Test
    fun `should apply the change and show the new value when a setting changes`() {
        // Given
        val change = SettingChange.MaxTransactions(count = 100)

        // When
        sut.onIntent(intent = SettingsUiIntent.SettingChanged(change = change))

        // Then
        assertEquals(listOf<SettingChange>(change), settingsRepository.changes)
        assertEquals(100, state.content.settings.maxTransactions)
    }

    @Test
    fun `should redact the typed header in lower case and close the field when confirmed`() {
        // Given
        sut.onIntent(intent = SettingsUiIntent.AddHeaderClicked)
        sut.onIntent(intent = SettingsUiIntent.HeaderTyped(input = "  X-Api-Key "))

        // When
        sut.onIntent(intent = SettingsUiIntent.AddHeaderConfirmed)

        // Then
        assertEquals(setOf("authorization", "x-api-key"), state.content.settings.redactedHeaders)
        assertFalse(state.content.isAddHeaderOpen)
        assertEquals("", state.content.addHeaderInput)
    }

    @Test
    fun `should close the field without saving when a blank header is confirmed`() {
        // Given
        sut.onIntent(intent = SettingsUiIntent.AddHeaderClicked)
        sut.onIntent(intent = SettingsUiIntent.HeaderTyped(input = "   "))

        // When
        sut.onIntent(intent = SettingsUiIntent.AddHeaderConfirmed)

        // Then
        assertEquals(emptyList<SettingChange>(), settingsRepository.changes)
        assertFalse(state.content.isAddHeaderOpen)
    }

    @Test
    fun `should forget what was typed when the add field is closed`() {
        // Given
        sut.onIntent(intent = SettingsUiIntent.AddHeaderClicked)
        sut.onIntent(intent = SettingsUiIntent.HeaderTyped(input = "x-token"))

        // When
        sut.onIntent(intent = SettingsUiIntent.AddHeaderClicked)

        // Then
        assertEquals("", state.content.addHeaderInput)
    }

    @Test
    fun `should stop redacting the header when it is removed`() {
        // When
        sut.onIntent(intent = SettingsUiIntent.HeaderRemoved(header = "authorization"))

        // Then
        assertEquals(emptySet<String>(), state.content.settings.redactedHeaders)
    }

    @Test
    fun `should keep the transactions and close the dialog when clearing is cancelled`() = runTest {
        // Given
        sut.onIntent(intent = SettingsUiIntent.ClearAllClicked)

        // When
        sut.onIntent(intent = SettingsUiIntent.ClearAllCancelled)

        // Then
        assertFalse(state.content.isClearDialogOpen)
        assertEquals(1, transactionRepository.transactions.value.size)
    }

    @Test
    fun `should clear the transactions and leave when clearing is confirmed`() = runTest {
        // Given
        sut.onIntent(intent = SettingsUiIntent.ClearAllClicked)

        // When
        sut.onIntent(intent = SettingsUiIntent.ClearAllConfirmed)

        // Then
        assertTrue(transactionRepository.transactions.value.isEmpty())
        assertTrue(state is SettingsUiState.Navigating)
        assertFalse(state.content.isClearDialogOpen)
    }

    @Test
    fun `should go back to content when the navigation is handled`() = runTest {
        // Given
        sut.onIntent(intent = SettingsUiIntent.ClearAllConfirmed)

        // When
        sut.onIntent(intent = SettingsUiIntent.NavigationHandled)

        // Then
        assertTrue(state is SettingsUiState.Content)
    }
}
