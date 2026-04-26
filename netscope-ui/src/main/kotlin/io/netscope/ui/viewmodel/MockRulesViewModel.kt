package io.netscope.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.netscope.NetScope
import io.netscope.model.MockRule
import kotlinx.coroutines.flow.StateFlow

class MockRulesViewModel : ViewModel() {

    val rules: StateFlow<List<MockRule>> = NetScope.mockRules

    fun add(rule: MockRule)    = NetScope.addMockRule(rule)
    fun update(rule: MockRule) = NetScope.updateMockRule(rule)
    fun delete(id: String)     = NetScope.deleteMockRule(id)
    fun toggle(id: String)     = NetScope.toggleMockRule(id)

    /** Returns the rule with the given id, or null if not found. */
    fun findById(id: String): MockRule? = rules.value.find { it.id == id }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>) = MockRulesViewModel() as T
    }
}
