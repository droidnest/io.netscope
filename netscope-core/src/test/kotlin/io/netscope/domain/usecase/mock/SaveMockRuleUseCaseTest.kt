package io.netscope.domain.usecase.mock

import io.netscope.fake.FakeMockRuleRepository
import io.netscope.fake.aMockRule
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveMockRuleUseCaseTest {

    @Test
    fun `should append the rule when its id is new`() {
        // Given
        val existing = aMockRule(id = "a")
        val added = aMockRule(id = "b")
        val repository = FakeMockRuleRepository(rules = listOf(existing))

        // When
        SaveMockRuleUseCase(repository = repository)(rule = added)

        // Then
        assertEquals(listOf(existing, added), repository.rules.value)
    }

    @Test
    fun `should replace the rule when its id exists`() {
        // Given
        val edited = aMockRule(id = "a", name = "Edited")
        val repository = FakeMockRuleRepository(rules = listOf(aMockRule(id = "a")))

        // When
        SaveMockRuleUseCase(repository = repository)(rule = edited)

        // Then
        assertEquals(listOf(edited), repository.rules.value)
    }
}
