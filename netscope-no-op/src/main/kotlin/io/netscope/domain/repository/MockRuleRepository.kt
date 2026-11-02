package io.netscope.domain.repository

import io.netscope.model.MockRule
import kotlinx.coroutines.flow.StateFlow

/** No-op stub matching the real [MockRuleRepository] in netscope-core. */
interface MockRuleRepository {
    val rules: StateFlow<List<MockRule>>
    fun getById(id: String): MockRule?
    fun add(rule: MockRule)
    fun update(rule: MockRule)
    fun delete(id: String)
    fun toggle(id: String)
}
