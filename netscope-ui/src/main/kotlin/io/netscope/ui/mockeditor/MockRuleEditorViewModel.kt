package io.netscope.ui.mockeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.netscope.NetScope
import io.netscope.domain.usecase.mock.GetMockRuleUseCase
import io.netscope.domain.usecase.mock.SaveMockRuleUseCase
import io.netscope.ui.mockeditor.mapper.toFormUi
import io.netscope.ui.mockeditor.mapper.toMockRule
import io.netscope.ui.mockeditor.model.MockRuleFormUi
import io.netscope.ui.mvi.BaseViewModel
import io.netscope.ui.mvi.UiIntent
import io.netscope.ui.mvi.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Edits one mock rule, or a new one when the screen starts without a rule id. */
internal class MockRuleEditorViewModel(
    private val getMockRuleUseCase: GetMockRuleUseCase,
    private val saveMockRuleUseCase: SaveMockRuleUseCase,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow<UiState<MockRuleEditorUiState>>(UiState.Idle)
    val uiState: StateFlow<UiState<MockRuleEditorUiState>> = _uiState.asStateFlow()

    override fun onIntent(intent: UiIntent) {
        if (intent !is MockRuleEditorUiIntent) return
        when (intent) {
            is MockRuleEditorUiIntent.Started -> start(ruleId = intent.ruleId)
            is MockRuleEditorUiIntent.NameTyped -> edit { copy(name = intent.name) }
            is MockRuleEditorUiIntent.UrlPatternTyped -> edit { copy(urlPattern = intent.urlPattern) }
            is MockRuleEditorUiIntent.MatchTypeSelected -> edit { copy(matchType = intent.matchType) }
            is MockRuleEditorUiIntent.MethodSelected -> edit { copy(method = intent.method) }
            is MockRuleEditorUiIntent.StatusCodeTyped -> edit {
                copy(statusCode = intent.statusCode.filter(Char::isDigit).take(n = STATUS_CODE_LENGTH))
            }
            is MockRuleEditorUiIntent.ContentTypeTyped -> edit { copy(contentType = intent.contentType) }
            is MockRuleEditorUiIntent.BodyTyped -> edit { copy(body = intent.body) }
            is MockRuleEditorUiIntent.DelayTyped -> edit {
                copy(delayMs = intent.delayMs.filter(Char::isDigit).take(n = DELAY_LENGTH))
            }
            is MockRuleEditorUiIntent.EnabledToggled -> edit { copy(isEnabled = intent.isEnabled) }
            MockRuleEditorUiIntent.SaveClicked -> save()
            MockRuleEditorUiIntent.NavigationHandled -> edit { this }
        }
    }

    private fun start(ruleId: String?) {
        if (_uiState.value != UiState.Idle) return
        val rule = ruleId?.let { getMockRuleUseCase(id = it) }
        show(form = rule?.toFormUi() ?: MockRuleFormUi())
    }

    private fun save() {
        val state = _uiState.value.dataOrNull() as? MockRuleEditorUiState.Content ?: return
        if (!state.form.isValid) return
        saveMockRuleUseCase(rule = state.form.toMockRule())
        _uiState.value = UiState.Success(data = MockRuleEditorUiState.Navigating(form = state.form))
    }

    private fun edit(transform: MockRuleFormUi.() -> MockRuleFormUi) {
        _uiState.value.dataOrNull()?.let { show(form = it.form.transform()) }
    }

    private fun show(form: MockRuleFormUi) {
        _uiState.value = UiState.Success(data = MockRuleEditorUiState.Content(form = form))
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = NetScope.mockRuleRepository
            return MockRuleEditorViewModel(
                getMockRuleUseCase = GetMockRuleUseCase(repository = repository),
                saveMockRuleUseCase = SaveMockRuleUseCase(repository = repository),
            ) as T
        }
    }

    private companion object {
        const val STATUS_CODE_LENGTH = 3
        const val DELAY_LENGTH = 6
    }
}
