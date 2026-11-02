package io.netscope.ui.fake

import io.netscope.NotificationStyle
import io.netscope.domain.repository.MockRuleRepository
import io.netscope.domain.repository.SettingsRepository
import io.netscope.domain.repository.TransactionRepository
import io.netscope.model.MockRule
import io.netscope.model.NetTransaction
import io.netscope.model.SettingChange
import io.netscope.model.Settings
import io.netscope.model.TrafficSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeTransactionRepository(
    transactions: List<NetTransaction> = emptyList(),
) : TransactionRepository {

    val transactions = MutableStateFlow(transactions)

    override suspend fun insert(transaction: NetTransaction): Long = transaction.id

    override suspend fun update(transaction: NetTransaction) = Unit

    override fun observeAll(): Flow<List<NetTransaction>> = transactions

    override fun observeSummary(): Flow<TrafficSummary> = transactions.map { list ->
        TrafficSummary(
            requestCount = list.size,
            errorCount = list.count { it.error != null || (it.responseCode ?: 0) >= 400 },
            lastResponseCode = list.firstOrNull()?.responseCode,
        )
    }

    override suspend fun getById(id: Long): NetTransaction? = transactions.value.find { it.id == id }

    override fun observeById(id: Long): Flow<NetTransaction?> =
        transactions.map { list -> list.find { it.id == id } }

    override suspend fun updateTiming(id: Long, dnsMs: Long?, connectMs: Long?, sslMs: Long?, ttfbMs: Long?) = Unit

    override suspend fun clearAll() {
        transactions.value = emptyList()
    }
}

class FakeMockRuleRepository(
    rules: List<MockRule> = emptyList(),
) : MockRuleRepository {

    private val _rules = MutableStateFlow(rules)
    override val rules: StateFlow<List<MockRule>> = _rules

    override fun getById(id: String): MockRule? = _rules.value.find { it.id == id }

    override fun add(rule: MockRule) = _rules.update { it + rule }

    override fun update(rule: MockRule) = _rules.update { list -> list.map { if (it.id == rule.id) rule else it } }

    override fun delete(id: String) = _rules.update { list -> list.filter { it.id != id } }

    override fun toggle(id: String) = _rules.update { list ->
        list.map { if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it }
    }
}

class FakeSettingsRepository(
    private var current: Settings = aSettings(),
) : SettingsRepository {

    val changes = mutableListOf<SettingChange>()

    override fun getSettings(): Settings = current

    override fun update(change: SettingChange) {
        changes += change
        if (change is SettingChange.RedactedHeaders) current = current.copy(redactedHeaders = change.headers)
        if (change is SettingChange.MaxTransactions) current = current.copy(maxTransactions = change.count)
    }
}

fun aSettings(
    maxTransactions: Int = 500,
    redactedHeaders: Set<String> = setOf("authorization"),
    lockedRedactedHeaders: Set<String> = emptySet(),
) = Settings(
    maxTransactions = maxTransactions,
    maxBodySize = 250_000L,
    alwaysReadResponseBody = true,
    redactedHeaders = redactedHeaders,
    shakeToOpen = true,
    notificationStyle = NotificationStyle.DETAILED,
    lockedRedactedHeaders = lockedRedactedHeaders,
)

fun aTransaction(
    id: Long = 1L,
    method: String = "GET",
    path: String = "/users",
    responseCode: Int? = 200,
) = NetTransaction(
    id = id,
    requestDate = 0L,
    method = method,
    scheme = "https",
    host = "api.example.com",
    path = path,
    query = null,
    requestHeaders = emptyList(),
    requestBody = null,
    requestContentType = null,
    requestBodySize = 0L,
    responseCode = responseCode,
)

fun aMockRule(
    id: String = "rule",
    name: String = "Login",
) = MockRule(id = id, name = name, urlPattern = "/login", statusCode = 201, delayMs = 30L)
