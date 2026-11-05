package io.netscope.domain.usecase.mock

import io.netscope.domain.repository.MockRuleRepository
import io.netscope.model.MockRule

/** Finds one mock rule. */
class GetMockRuleUseCase(
    private val repository: MockRuleRepository,
) {

    /** Returns the rule with this [id], or null if there is none. */
    operator fun invoke(id: String): MockRule? = repository.getById(id = id)
}
