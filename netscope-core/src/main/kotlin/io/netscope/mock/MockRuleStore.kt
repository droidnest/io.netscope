package io.netscope.mock

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.netscope.model.MockRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * In-memory store for [MockRule] instances, backed by SharedPreferences for persistence
 * across app restarts. Mutations are atomic ([MutableStateFlow.update]) and the latest
 * list is persisted after each one.
 */
internal class MockRuleStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _rules = MutableStateFlow(load())
    val rules: StateFlow<List<MockRule>> = _rules.asStateFlow()

    fun add(rule: MockRule) = mutate { it + rule }

    fun update(rule: MockRule) = mutate { list ->
        list.map { if (it.id == rule.id) rule else it }
    }

    fun delete(id: String) = mutate { list -> list.filter { it.id != id } }

    fun toggle(id: String) = mutate { list ->
        list.map { if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it }
    }

    private fun mutate(transform: (List<MockRule>) -> List<MockRule>) {
        _rules.update(transform)
        // Persist the current value under a lock: whichever write lands last holds the latest list.
        synchronized(prefs) {
            prefs.edit().putString(KEY_RULES, gson.toJson(_rules.value)).apply()
        }
    }

    private fun load(): List<MockRule> {
        val json = prefs.getString(KEY_RULES, null) ?: return emptyList()
        return decodeMockRules(json, gson)
    }

    private companion object {
        const val PREFS_NAME = "netscope_mock_rules"
        const val KEY_RULES  = "rules"
    }
}

// ── Decoding ──────────────────────────────────────────────────────────────────

/**
 * Parses persisted rules. Gson bypasses Kotlin constructors, so fields missing from older JSON
 * arrive as `null` even when declared non-null — each rule is rebuilt with the declared defaults
 * (a rule missing its URL pattern is disabled rather than matching everything).
 * Malformed JSON yields an empty list.
 */
internal fun decodeMockRules(json: String, gson: Gson = Gson()): List<MockRule> {
    val type = object : TypeToken<List<MockRule?>>() {}.type
    val parsed = runCatching { gson.fromJson<List<MockRule?>?>(json, type) }.getOrNull() ?: return emptyList()
    return parsed.filterIsInstance<MockRule>().map { it.sanitized() }
}

private fun MockRule.sanitized(): MockRule = MockRule(
    id           = id.orDefault(UUID.randomUUID().toString()),
    name         = name.orDefault(""),
    urlPattern   = urlPattern.orDefault(""),
    matchType    = matchType.orDefault(MockRule.MatchType.CONTAINS),
    method       = method,
    statusCode   = statusCode,
    contentType  = contentType.orDefault("application/json"),
    responseBody = responseBody.orDefault("{}"),
    delayMs      = delayMs,
    // A rule without a pattern would mock every request (CONTAINS "") — keep it, but disabled.
    isEnabled    = isEnabled && urlPattern.orNull() != null,
)

/** Runtime null check the compiler can't elide, even when the static type says non-null. */
private fun <T : Any> T?.orDefault(default: T): T = this ?: default

private fun <T : Any> T?.orNull(): T? = this
