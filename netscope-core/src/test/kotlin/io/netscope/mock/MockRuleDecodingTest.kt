package io.netscope.mock

import io.netscope.model.MockRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockRuleDecodingTest {

    @Test
    fun `missing fields fall back to declared defaults`() {
        val rules = decodeMockRules("""[{"id":"1","urlPattern":"/api"}, null]""")

        val rule = rules.single()
        assertEquals("1", rule.id)
        assertEquals("", rule.name)
        assertEquals(MockRule.MatchType.CONTAINS, rule.matchType)
        assertEquals("application/json", rule.contentType)
        assertEquals("{}", rule.responseBody)
        assertTrue(rule.matches("https://x.com/api", "GET"))
    }

    @Test
    fun `rule without a URL pattern is kept but disabled`() {
        val rule = decodeMockRules("""[{"id":"2","isEnabled":true}]""").single()

        assertEquals("", rule.urlPattern)
        assertFalse(rule.isEnabled)
    }

    @Test
    fun `malformed or null JSON yields an empty list`() {
        assertTrue(decodeMockRules("not json").isEmpty())
        assertTrue(decodeMockRules("null").isEmpty())
    }

    @Test
    fun `round-trips through Gson unchanged`() {
        val original = listOf(MockRule(id = "3", name = "n", urlPattern = ".*", matchType = MockRule.MatchType.REGEX, method = "GET"))
        assertEquals(original, decodeMockRules(com.google.gson.Gson().toJson(original)))
    }
}
