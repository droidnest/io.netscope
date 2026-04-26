package io.netscope.mock

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.netscope.model.MockRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory store for [MockRule] instances, backed by SharedPreferences for persistence
 * across app restarts. All mutations are synchronous and thread-safe via the StateFlow.
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
        val next = transform(_rules.value)
        _rules.value = next
        prefs.edit().putString(KEY_RULES, gson.toJson(next)).apply()
    }

    private fun load(): List<MockRule> {
        val json = prefs.getString(KEY_RULES, null) ?: return emptyList()
        val type = object : TypeToken<List<MockRule>>() {}.type
        return runCatching { gson.fromJson<List<MockRule>>(json, type) }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFS_NAME = "netscope_mock_rules"
        const val KEY_RULES  = "rules"
    }
}
