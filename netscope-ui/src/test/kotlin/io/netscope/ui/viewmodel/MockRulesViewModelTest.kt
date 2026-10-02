package io.netscope.ui.viewmodel

import io.netscope.model.MockRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockRulesViewModelTest {

    private class FakeMockRulesGateway : MockRulesGateway {
        private val _rules = MutableStateFlow<List<MockRule>>(emptyList())
        override val rules: StateFlow<List<MockRule>> = _rules

        override fun add(rule: MockRule)    { _rules.value = _rules.value + rule }
        override fun update(rule: MockRule) { _rules.value = _rules.value.map { if (it.id == rule.id) rule else it } }
        override fun delete(id: String)     { _rules.value = _rules.value.filterNot { it.id == id } }
        override fun toggle(id: String)     {
            _rules.value = _rules.value.map { if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it }
        }
    }

    private val mockRulesGateway = FakeMockRulesGateway()
    private val viewModel = MockRulesViewModel(mockRulesGateway = mockRulesGateway)
    private val rule = MockRule(id = "r1", name = "Login", urlPattern = "/api/login")

    @Test
    fun `add delegates to gateway and is visible in rules`() {
        // When
        viewModel.add(rule)

        // Then
        assertEquals(listOf(rule), viewModel.rules.value)
        assertEquals(rule, viewModel.findById("r1"))
    }

    @Test
    fun `toggle delegates to gateway`() {
        // Given
        viewModel.add(rule)

        // When
        viewModel.toggle("r1")

        // Then
        assertFalse(viewModel.findById("r1")!!.isEnabled)
    }

    @Test
    fun `update delegates to gateway`() {
        // Given
        viewModel.add(rule)

        // When
        viewModel.update(rule.copy(statusCode = 500))

        // Then
        assertEquals(500, viewModel.findById("r1")!!.statusCode)
    }

    @Test
    fun `delete delegates to gateway`() {
        // Given
        viewModel.add(rule)

        // When
        viewModel.delete("r1")

        // Then
        assertTrue(viewModel.rules.value.isEmpty())
        assertNull(viewModel.findById("r1"))
    }
}
