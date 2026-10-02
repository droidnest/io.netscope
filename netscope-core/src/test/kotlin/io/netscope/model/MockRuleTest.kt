package io.netscope.model

import com.google.gson.Gson
import io.netscope.model.MockRule.MatchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockRuleTest {

    private val url = "https://api.example.com/v1/users/42?full=true"

    private fun rule(pattern: String, type: MatchType, method: String? = null) =
        MockRule(id = "r", name = "r", urlPattern = pattern, matchType = type, method = method)

    @Test
    fun `CONTAINS matches a case-insensitive substring`() {
        assertTrue(rule("/V1/USERS", MatchType.CONTAINS).matches(url, "GET"))
        assertFalse(rule("/v2/", MatchType.CONTAINS).matches(url, "GET"))
    }

    @Test
    fun `EXACT matches the whole URL only`() {
        assertTrue(rule(url.uppercase(), MatchType.EXACT).matches(url, "GET"))
        assertFalse(rule("https://api.example.com/v1/users/42", MatchType.EXACT).matches(url, "GET"))
    }

    @Test
    fun `REGEX matches the whole URL and is reusable`() {
        val r = rule(""".*/users/\d+\?.*""", MatchType.REGEX)
        assertTrue(r.matches(url, "GET"))
        assertTrue(r.matches(url, "GET")) // second call hits the cached Regex
        assertFalse(r.matches("https://api.example.com/v1/users/me", "GET"))
    }

    @Test
    fun `invalid REGEX never matches and does not throw`() {
        val r = rule("([unclosed", MatchType.REGEX)
        assertFalse(r.matches(url, "GET"))
        assertFalse(r.matches(url, "GET"))
    }

    @Test
    fun `method filter is case-insensitive and null matches any method`() {
        val postOnly = rule("/users", MatchType.CONTAINS, method = "post")
        assertTrue(postOnly.matches(url, "POST"))
        assertFalse(postOnly.matches(url, "GET"))
        assertTrue(rule("/users", MatchType.CONTAINS).matches(url, "DELETE"))
    }

    @Test
    fun `regex cache is invisible to equality and Gson`() {
        val a = rule(".*users.*", MatchType.REGEX)
        val b = a.copy()
        a.matches(url, "GET")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        val json = Gson().toJson(a)
        assertFalse(json, json.contains("compiled") || json.contains("regexInvalid"))
        val restored = Gson().fromJson(json, MockRule::class.java)
        assertEquals(a, restored)
        assertTrue(restored.matches(url, "GET"))
    }
}
