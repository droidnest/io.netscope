package io.netscope.ui.mockeditor

import io.netscope.ui.mockeditor.model.MockRuleFormUi

/** State of the mock rule editor. */
internal sealed interface MockRuleEditorUiState {

    val form: MockRuleFormUi

    data class Content(override val form: MockRuleFormUi) : MockRuleEditorUiState

    /** The rule was saved: the screen leaves. */
    data class Navigating(override val form: MockRuleFormUi) : MockRuleEditorUiState
}
