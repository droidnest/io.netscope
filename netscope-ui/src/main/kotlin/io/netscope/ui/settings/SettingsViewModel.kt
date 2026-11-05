package io.netscope.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.netscope.NetScope
import io.netscope.domain.usecase.settings.GetSettingsUseCase
import io.netscope.domain.usecase.settings.UpdateSettingUseCase
import io.netscope.domain.usecase.transaction.ClearTransactionsUseCase
import io.netscope.model.SettingChange
import io.netscope.ui.mvi.BaseViewModel
import io.netscope.ui.mvi.UiIntent
import io.netscope.ui.mvi.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Shows what the library runs with: the app's configuration plus what the user saved. */
internal class SettingsViewModel(
    private val getSettingsUseCase: GetSettingsUseCase,
    private val updateSettingUseCase: UpdateSettingUseCase,
    private val clearTransactionsUseCase: ClearTransactionsUseCase,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow<UiState<SettingsUiState>>(
        UiState.Success(
            data = SettingsUiState.Content(content = SettingsContentUi(settings = getSettingsUseCase())),
        ),
    )
    val uiState: StateFlow<UiState<SettingsUiState>> = _uiState.asStateFlow()

    private val content: SettingsContentUi?
        get() = _uiState.value.dataOrNull()?.content

    override fun onIntent(intent: UiIntent) {
        if (intent !is SettingsUiIntent) return
        val current = content ?: return
        when (intent) {
            is SettingsUiIntent.SettingChanged -> apply(change = intent.change, content = current)
            SettingsUiIntent.AddHeaderClicked -> show(
                content = current.copy(isAddHeaderOpen = !current.isAddHeaderOpen, addHeaderInput = ""),
            )
            is SettingsUiIntent.HeaderTyped -> show(content = current.copy(addHeaderInput = intent.input))
            SettingsUiIntent.AddHeaderConfirmed -> addHeader(content = current)
            is SettingsUiIntent.HeaderRemoved -> removeHeader(header = intent.header, content = current)
            SettingsUiIntent.ClearAllClicked -> show(content = current.copy(isClearDialogOpen = true))
            SettingsUiIntent.ClearAllCancelled -> show(content = current.copy(isClearDialogOpen = false))
            SettingsUiIntent.ClearAllConfirmed -> clearAll(content = current)
            SettingsUiIntent.NavigationHandled -> show(content = current)
        }
    }

    private fun addHeader(content: SettingsContentUi) {
        val closed = content.copy(isAddHeaderOpen = false, addHeaderInput = "")
        val header = content.addHeaderInput.trim().lowercase()
        if (header.isEmpty()) {
            show(content = closed)
        } else {
            apply(
                change = SettingChange.RedactedHeaders(headers = content.settings.redactedHeaders + header),
                content = closed,
            )
        }
    }

    private fun removeHeader(header: String, content: SettingsContentUi) {
        if (header in content.settings.lockedRedactedHeaders) return
        apply(
            change = SettingChange.RedactedHeaders(headers = content.settings.redactedHeaders - header),
            content = content,
        )
    }

    private fun apply(change: SettingChange, content: SettingsContentUi) {
        updateSettingUseCase(change = change)
        show(content = content.copy(settings = getSettingsUseCase()))
    }

    private fun clearAll(content: SettingsContentUi) {
        val closed = content.copy(isClearDialogOpen = false)
        show(content = closed)
        viewModelScope.launch {
            clearTransactionsUseCase()
            _uiState.value = UiState.Success(data = SettingsUiState.Navigating(content = closed))
        }
    }

    private fun show(content: SettingsContentUi) {
        _uiState.value = UiState.Success(data = SettingsUiState.Content(content = content))
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val settingsRepository = NetScope.settingsRepository
            return SettingsViewModel(
                getSettingsUseCase = GetSettingsUseCase(repository = settingsRepository),
                updateSettingUseCase = UpdateSettingUseCase(repository = settingsRepository),
                clearTransactionsUseCase = ClearTransactionsUseCase(repository = NetScope.repository),
            ) as T
        }
    }
}
