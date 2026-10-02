package io.netscope.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.netscope.NetScope
import io.netscope.model.MockRule
import kotlinx.coroutines.flow.StateFlow

// ── Gateway ───────────────────────────────────────────────────────────────────

/** Mock-rule operations the UI needs. Seam over the global [NetScope] so the ViewModel can be tested with a fake. */
interface MockRulesGateway {
    val rules: StateFlow<List<MockRule>>
    fun add(rule: MockRule)
    fun update(rule: MockRule)
    fun delete(id: String)
    fun toggle(id: String)
}

/** Default [MockRulesGateway] backed by [NetScope]. */
object NetScopeMockRulesGateway : MockRulesGateway {
    override val rules: StateFlow<List<MockRule>> get() = NetScope.mockRules
    override fun add(rule: MockRule)    { NetScope.addMockRule(rule) }
    override fun update(rule: MockRule) { NetScope.updateMockRule(rule) }
    override fun delete(id: String)     { NetScope.deleteMockRule(id) }
    override fun toggle(id: String)     { NetScope.toggleMockRule(id) }
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class MockRulesViewModel(
    private val mockRulesGateway: MockRulesGateway = NetScopeMockRulesGateway,
) : ViewModel() {

    val rules: StateFlow<List<MockRule>> get() = mockRulesGateway.rules

    fun add(rule: MockRule)    = mockRulesGateway.add(rule)
    fun update(rule: MockRule) = mockRulesGateway.update(rule)
    fun delete(id: String)     = mockRulesGateway.delete(id)
    fun toggle(id: String)     = mockRulesGateway.toggle(id)

    /** Returns the rule with the given id, or null if not found. */
    fun findById(id: String): MockRule? = rules.value.find { it.id == id }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>) = MockRulesViewModel() as T
    }
}
