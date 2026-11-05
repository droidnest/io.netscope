package io.netscope.ui.mockeditor

import io.netscope.domain.usecase.mock.GetMockRuleUseCase
import io.netscope.domain.usecase.mock.SaveMockRuleUseCase
import io.netscope.model.MockRule
import io.netscope.ui.fake.FakeMockRuleRepository
import io.netscope.ui.fake.aMockRule
import io.netscope.ui.mockeditor.model.MockRuleFormUi
import io.netscope.ui.mvi.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockRuleEditorViewModelTest {

    private val rule = aMockRule(id = "a", name = "Login")
    private val repository = FakeMockRuleRepository(rules = listOf(rule))

    private val sut = MockRuleEditorViewModel(
        getMockRuleUseCase = GetMockRuleUseCase(repository = repository),
        saveMockRuleUseCase = SaveMockRuleUseCase(repository = repository),
    )

    private val state: MockRuleEditorUiState
        get() = sut.uiState.value.dataOrNull()!!

    private fun fillValidForm() {
        sut.onIntent(intent = MockRuleEditorUiIntent.NameTyped(name = " New "))
        sut.onIntent(intent = MockRuleEditorUiIntent.UrlPatternTyped(urlPattern = " /new "))
    }

    @Test
    fun `should stay idle when the screen has not started`() {
        assertEquals(UiState.Idle, sut.uiState.value)
    }

    @Test
    fun `should start with the defaults when there is no rule id`() {
        // When
        sut.onIntent(intent = MockRuleEditorUiIntent.Started(ruleId = null))

        // Then
        assertEquals(MockRuleFormUi(), state.form)
    }

    @Test
    fun `should fill the form with the rule when it exists`() {
        // When
        sut.onIntent(intent = MockRuleEditorUiIntent.Started(ruleId = "a"))

        // Then
        assertEquals("a", state.form.ruleId)
        assertEquals("Login", state.form.name)
        assertEquals("201", state.form.statusCode)
        assertEquals("30", state.form.delayMs)
    }

    @Test
    fun `should start as a new rule when the rule no longer exists`() {
        // When
        sut.onIntent(intent = MockRuleEditorUiIntent.Started(ruleId = "gone"))

        // Then
        assertNull(state.form.ruleId)
    }

    @Test
    fun `should keep what was typed when started again`() {
        // Given
        sut.onIntent(intent = MockRuleEditorUiIntent.Started(ruleId = "a"))
        sut.onIntent(intent = MockRuleEditorUiIntent.NameTyped(name = "Typed"))

        // When
        sut.onIntent(intent = MockRuleEditorUiIntent.Started(ruleId = "a"))

        // Then
        assertEquals("Typed", state.form.name)
    }

    @Test
    fun `should keep three digits when a status code is typed`() {
        // Given
        sut.onIntent(intent = MockRuleEditorUiIntent.Started(ruleId = null))

        // When
        sut.onIntent(intent = MockRuleEditorUiIntent.StatusCodeTyped(statusCode = "4a041"))

        // Then
        assertEquals("404", state.form.statusCode)
    }

    @Test
    fun `should keep six digits when a delay is typed`() {
        // Given
        sut.onIntent(intent = MockRuleEditorUiIntent.Started(ruleId = null))

        // When
        sut.onIntent(intent = MockRuleEditorUiIntent.DelayTyped(delayMs = "12x34567"))

        // Then
        assertEquals("123456", state.form.delayMs)
    }

    @Test
    fun `should be invalid when the status code is out of range`() {
        // Given
        sut.onIntent(intent = MockRuleEditorUiIntent.Started(ruleId = null))
        fillValidForm()

        // When
        sut.onIntent(intent = MockRuleEditorUiIntent.StatusCodeTyped(statusCode = "99"))

        // Then
        assertFalse(state.form.isValid)
    }

    @Test
    fun `should save nothing when the form is invalid`() {
        // Given
        sut.onIntent(intent = MockRuleEditorUiIntent.Started(ruleId = null))

        // When
        sut.onIntent(intent = MockRuleEditorUiIntent.SaveClicked)

        // Then
        assertEquals(listOf(rule), repository.rules.value)
        assertTrue(state is MockRuleEditorUiState.Content)
    }

    @Test
    fun `should add a trimmed rule and leave when a new rule is saved`() {
        // Given
        sut.onIntent(intent = MockRuleEditorUiIntent.Started(ruleId = null))
        fillValidForm()

        // When
        sut.onIntent(intent = MockRuleEditorUiIntent.SaveClicked)

        // Then
        val saved = repository.rules.value.last()
        assertEquals(2, repository.rules.value.size)
        assertEquals("New", saved.name)
        assertEquals("/new", saved.urlPattern)
        assertTrue(state is MockRuleEditorUiState.Navigating)
    }

    @Test
    fun `should replace the rule when an existing rule is saved`() {
        // Given
        sut.onIntent(intent = MockRuleEditorUiIntent.Started(ruleId = "a"))
        sut.onIntent(intent = MockRuleEditorUiIntent.NameTyped(name = "Edited"))

        // When
        sut.onIntent(intent = MockRuleEditorUiIntent.SaveClicked)

        // Then
        assertEquals(listOf(rule.copy(name = "Edited")), repository.rules.value)
    }

    @Test
    fun `should save once when save is clicked twice`() {
        // Given
        sut.onIntent(intent = MockRuleEditorUiIntent.Started(ruleId = null))
        fillValidForm()

        // When
        sut.onIntent(intent = MockRuleEditorUiIntent.SaveClicked)
        sut.onIntent(intent = MockRuleEditorUiIntent.SaveClicked)

        // Then
        assertEquals(2, repository.rules.value.size)
    }

    @Test
    fun `should save once when save is clicked again during the exit transition`() {
        // Given
        sut.onIntent(intent = MockRuleEditorUiIntent.Started(ruleId = null))
        fillValidForm()
        sut.onIntent(intent = MockRuleEditorUiIntent.SaveClicked)
        sut.onIntent(intent = MockRuleEditorUiIntent.NavigationHandled)

        // When
        sut.onIntent(intent = MockRuleEditorUiIntent.SaveClicked)

        // Then
        assertEquals(2, repository.rules.value.size)
    }

    @Test
    fun `should not save when the content type has a character a header cannot carry`() {
        // Given
        sut.onIntent(intent = MockRuleEditorUiIntent.Started(ruleId = null))
        fillValidForm()
        sut.onIntent(intent = MockRuleEditorUiIntent.ContentTypeTyped(contentType = "application/jsön"))

        // When
        sut.onIntent(intent = MockRuleEditorUiIntent.SaveClicked)

        // Then
        assertEquals(1, repository.rules.value.size)
    }

    @Test
    fun `should keep the match type when it is selected`() {
        // Given
        sut.onIntent(intent = MockRuleEditorUiIntent.Started(ruleId = null))

        // When
        sut.onIntent(intent = MockRuleEditorUiIntent.MatchTypeSelected(matchType = MockRule.MatchType.REGEX))

        // Then
        assertEquals(MockRule.MatchType.REGEX, state.form.matchType)
    }
}
