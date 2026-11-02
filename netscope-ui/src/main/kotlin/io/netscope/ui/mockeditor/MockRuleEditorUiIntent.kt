package io.netscope.ui.mockeditor

import io.netscope.model.MockRule
import io.netscope.ui.mvi.UiIntent

internal sealed interface MockRuleEditorUiIntent : UiIntent {
    data class Started(val ruleId: String?) : MockRuleEditorUiIntent
    data class NameTyped(val name: String) : MockRuleEditorUiIntent
    data class UrlPatternTyped(val urlPattern: String) : MockRuleEditorUiIntent
    data class MatchTypeSelected(val matchType: MockRule.MatchType) : MockRuleEditorUiIntent
    data class MethodSelected(val method: String?) : MockRuleEditorUiIntent
    data class StatusCodeTyped(val statusCode: String) : MockRuleEditorUiIntent
    data class ContentTypeTyped(val contentType: String) : MockRuleEditorUiIntent
    data class BodyTyped(val body: String) : MockRuleEditorUiIntent
    data class DelayTyped(val delayMs: String) : MockRuleEditorUiIntent
    data class EnabledToggled(val isEnabled: Boolean) : MockRuleEditorUiIntent
    data object SaveClicked : MockRuleEditorUiIntent
    data object NavigationHandled : MockRuleEditorUiIntent
}
