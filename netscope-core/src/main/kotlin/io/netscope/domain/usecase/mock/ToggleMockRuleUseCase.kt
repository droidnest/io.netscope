package io.netscope.domain.usecase.mock

import io.netscope.domain.repository.MockRuleRepository

/** Enables or disables one mock rule. */
class ToggleMockRuleUseCase(
    private val repository: MockRuleRepository,
) {

    /** Enables the rule with this [id] when it is disabled, and the other way round. */
    operator fun invoke(id: String) = repository.toggle(id = id)
}
