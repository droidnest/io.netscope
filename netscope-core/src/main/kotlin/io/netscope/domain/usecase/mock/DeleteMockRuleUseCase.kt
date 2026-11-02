package io.netscope.domain.usecase.mock

import io.netscope.domain.repository.MockRuleRepository

/** Deletes one mock rule. */
class DeleteMockRuleUseCase(
    private val repository: MockRuleRepository,
) {

    /** Removes the rule with this [id]. */
    operator fun invoke(id: String) = repository.delete(id = id)
}
