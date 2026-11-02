package io.netscope.ui.mockrules

import io.netscope.domain.usecase.mock.DeleteMockRuleUseCase
import io.netscope.domain.usecase.mock.ObserveMockRulesUseCase
import io.netscope.domain.usecase.mock.ToggleMockRuleUseCase
import io.netscope.model.MockRule
import io.netscope.ui.MainDispatcherRule
import io.netscope.ui.fake.FakeMockRuleRepository
import io.netscope.ui.fake.aMockRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MockRulesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val rule = aMockRule(id = "a")
    private val repository = FakeMockRuleRepository(rules = listOf(rule))

    private lateinit var sut: MockRulesViewModel

    @Before
    fun setUp() {
        sut = MockRulesViewModel(
            observeMockRulesUseCase = ObserveMockRulesUseCase(repository = repository),
            toggleMockRuleUseCase = ToggleMockRuleUseCase(repository = repository),
            deleteMockRuleUseCase = DeleteMockRuleUseCase(repository = repository),
        )
    }

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sut.uiState.collect {} }
    }

    private val state: MockRulesUiState
        get() = sut.uiState.value.dataOrNull()!!

    @Test
    fun `should show the rules when collected`() = runTest {
        // Given
        collectState()

        // Then
        assertEquals(listOf(rule), state.rules)
    }

    @Test
    fun `should disable the rule when it is toggled`() = runTest {
        // Given
        collectState()

        // When
        sut.onIntent(intent = MockRulesUiIntent.RuleToggled(id = "a"))

        // Then
        assertFalse(state.rules.single().isEnabled)
    }

    @Test
    fun `should remove the rule when it is deleted`() = runTest {
        // Given
        collectState()

        // When
        sut.onIntent(intent = MockRulesUiIntent.RuleDeleted(id = "a"))

        // Then
        assertEquals(emptyList<MockRule>(), state.rules)
    }

    @Test
    fun `should navigate to a new rule when add is clicked`() = runTest {
        // Given
        collectState()

        // When
        sut.onIntent(intent = MockRulesUiIntent.AddClicked)

        // Then
        assertEquals(MockRulesDestination.NewRule, (state as MockRulesUiState.Navigating).destination)
    }

    @Test
    fun `should navigate to the rule when it is clicked`() = runTest {
        // Given
        collectState()

        // When
        sut.onIntent(intent = MockRulesUiIntent.RuleClicked(id = "a"))

        // Then
        assertEquals(MockRulesDestination.EditRule(id = "a"), (state as MockRulesUiState.Navigating).destination)
    }

    @Test
    fun `should go back to content when the navigation is handled`() = runTest {
        // Given
        collectState()
        sut.onIntent(intent = MockRulesUiIntent.AddClicked)

        // When
        sut.onIntent(intent = MockRulesUiIntent.NavigationHandled)

        // Then
        assertTrue(state is MockRulesUiState.Content)
    }
}
