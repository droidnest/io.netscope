package io.netscope.ui.mockrules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.netscope.NetScope
import io.netscope.domain.usecase.mock.DeleteMockRuleUseCase
import io.netscope.domain.usecase.mock.ObserveMockRulesUseCase
import io.netscope.domain.usecase.mock.ToggleMockRuleUseCase
import io.netscope.ui.mvi.BaseViewModel
import io.netscope.ui.mvi.UiIntent
import io.netscope.ui.mvi.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Lists the mock rules and enables, disables or deletes them. */
internal class MockRulesViewModel(
    observeMockRulesUseCase: ObserveMockRulesUseCase,
    private val toggleMockRuleUseCase: ToggleMockRuleUseCase,
    private val deleteMockRuleUseCase: DeleteMockRuleUseCase,
) : BaseViewModel() {

    private val destination = MutableStateFlow<MockRulesDestination?>(null)

    val uiState: StateFlow<UiState<MockRulesUiState>> =
        combine(observeMockRulesUseCase(), destination) { rules, destination ->
            UiState.Success(
                data = when (destination) {
                    null -> MockRulesUiState.Content(rules = rules)
                    else -> MockRulesUiState.Navigating(rules = rules, destination = destination)
                },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = UiState.Success(data = MockRulesUiState.Content(rules = emptyList())),
        )

    override fun onIntent(intent: UiIntent) {
        if (intent !is MockRulesUiIntent) return
        when (intent) {
            MockRulesUiIntent.AddClicked -> destination.value = MockRulesDestination.NewRule
            is MockRulesUiIntent.RuleClicked -> destination.value = MockRulesDestination.EditRule(id = intent.id)
            is MockRulesUiIntent.RuleToggled -> toggleMockRuleUseCase(id = intent.id)
            is MockRulesUiIntent.RuleDeleted -> deleteMockRuleUseCase(id = intent.id)
            MockRulesUiIntent.NavigationHandled -> destination.value = null
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = NetScope.mockRuleRepository
            return MockRulesViewModel(
                observeMockRulesUseCase = ObserveMockRulesUseCase(repository = repository),
                toggleMockRuleUseCase = ToggleMockRuleUseCase(repository = repository),
                deleteMockRuleUseCase = DeleteMockRuleUseCase(repository = repository),
            ) as T
        }
    }
}
