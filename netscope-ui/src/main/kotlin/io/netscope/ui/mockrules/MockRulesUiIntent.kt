package io.netscope.ui.mockrules

import io.netscope.ui.mvi.UiIntent

internal sealed interface MockRulesUiIntent : UiIntent {
    data object AddClicked : MockRulesUiIntent
    data class RuleClicked(val id: String) : MockRulesUiIntent
    data class RuleToggled(val id: String) : MockRulesUiIntent
    data class RuleDeleted(val id: String) : MockRulesUiIntent
    data object NavigationHandled : MockRulesUiIntent
}
