package io.netscope.domain.usecase.mock

import io.netscope.domain.repository.MockRuleRepository
import io.netscope.model.MockRule
import kotlinx.coroutines.flow.Flow

/** Follows the mock rules the user configured. */
class ObserveMockRulesUseCase(
    private val repository: MockRuleRepository,
) {

    /** Emits every rule, in the order they were added, on every change. */
    operator fun invoke(): Flow<List<MockRule>> = repository.rules
}
