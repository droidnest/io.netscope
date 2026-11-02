package io.netscope.domain.repository

import io.netscope.model.MockRule
import kotlinx.coroutines.flow.StateFlow

/** The mock rules the user configured, kept across app restarts. */
interface MockRuleRepository {

    /** Every rule, in the order they were added. */
    val rules: StateFlow<List<MockRule>>

    /** Returns the rule with this [id], or null if there is none. */
    fun getById(id: String): MockRule?

    /** Appends [rule] to the list. */
    fun add(rule: MockRule)

    /** Replaces the rule that has the same [MockRule.id]. */
    fun update(rule: MockRule)

    /** Removes the rule with this [id]. */
    fun delete(id: String)

    /** Enables the rule with this [id] when it is disabled, and the other way round. */
    fun toggle(id: String)
}
