package io.netscope.ui.mockeditor.model

import io.netscope.model.MockRule

/** The fields of the mock rule editor, as the user typed them. */
internal data class MockRuleFormUi(
    val ruleId: String? = null,
    val name: String = "",
    val urlPattern: String = "",
    val matchType: MockRule.MatchType = MockRule.MatchType.CONTAINS,
    val method: String? = null,
    val statusCode: String = "200",
    val contentType: String = "application/json",
    val body: String = "{}",
    val delayMs: String = "0",
    val isEnabled: Boolean = true,
) {
    val statusCodeValue: Int?
        get() = statusCode.toIntOrNull()

    val isValid: Boolean
        get() = name.isNotBlank() && urlPattern.isNotBlank() && statusCodeValue?.let { it in 100..599 } == true
}
