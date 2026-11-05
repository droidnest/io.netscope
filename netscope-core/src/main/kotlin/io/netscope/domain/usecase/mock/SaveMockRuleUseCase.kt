package io.netscope.domain.usecase.mock

import io.netscope.domain.repository.MockRuleRepository
import io.netscope.model.MockRule

/** Saves a mock rule the user created or edited. */
class SaveMockRuleUseCase(
    private val repository: MockRuleRepository,
) {

    /** Replaces the rule with the same id, or appends [rule] when there is none. */
    operator fun invoke(rule: MockRule) {
        if (repository.getById(id = rule.id) == null) {
            repository.add(rule = rule)
        } else {
            repository.update(rule = rule)
        }
    }
}
