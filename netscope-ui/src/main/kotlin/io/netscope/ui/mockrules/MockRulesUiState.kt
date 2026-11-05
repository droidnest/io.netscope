package io.netscope.ui.mockrules

import io.netscope.model.MockRule

/** State of the mock rule list. */
internal sealed interface MockRulesUiState {

    val rules: List<MockRule>

    data class Content(override val rules: List<MockRule>) : MockRulesUiState

    data class Navigating(
        override val rules: List<MockRule>,
        val destination: MockRulesDestination,
    ) : MockRulesUiState
}

/** Where the mock rule list goes next. */
internal sealed interface MockRulesDestination {
    data object NewRule : MockRulesDestination
    data class EditRule(val id: String) : MockRulesDestination
}
