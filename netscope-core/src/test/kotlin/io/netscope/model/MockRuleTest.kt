package io.netscope.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockRuleTest {

    private fun rule(
        pattern: String,
        matchType: MockRule.MatchType = MockRule.MatchType.CONTAINS,
        method: String? = null,
    ) = MockRule(name = "rule", urlPattern = pattern, matchType = matchType, method = method)

    @Test
    fun `should match ignoring case when the url contains the pattern`() {
        assertTrue(rule(pattern = "/LOGIN").matches(url = "https://a.com/login", requestMethod = "GET"))
    }

    @Test
    fun `should not match a longer url when the match type is exact`() {
        val rule = rule(pattern = "https://a.com/login", matchType = MockRule.MatchType.EXACT)

        assertFalse(rule.matches(url = "https://a.com/login/1", requestMethod = "GET"))
    }

    @Test
    fun `should match the whole url when the match type is regex`() {
        val rule = rule(pattern = ".*/users/\\d+", matchType = MockRule.MatchType.REGEX)

        assertTrue(rule.matches(url = "https://a.com/users/12", requestMethod = "GET"))
    }

    @Test
    fun `should not match instead of crashing when the regex is invalid`() {
        val rule = rule(pattern = "[", matchType = MockRule.MatchType.REGEX)

        assertFalse(rule.matches(url = "https://a.com/[", requestMethod = "GET"))
    }

    @Test
    fun `should match any method when the rule has none`() {
        assertTrue(rule(pattern = "/a").matches(url = "/a", requestMethod = "DELETE"))
    }

    @Test
    fun `should not match when the method differs`() {
        assertFalse(rule(pattern = "/a", method = "POST").matches(url = "/a", requestMethod = "GET"))
    }

    @Test
    fun `should match the method ignoring case when the rule has one`() {
        assertTrue(rule(pattern = "/a", method = "post").matches(url = "/a", requestMethod = "POST"))
    }
}
