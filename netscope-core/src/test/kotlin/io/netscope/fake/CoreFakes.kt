package io.netscope.fake

import io.netscope.domain.repository.MockRuleRepository
import io.netscope.domain.repository.TransactionRepository
import io.netscope.model.MockRule
import io.netscope.model.NetTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeTransactionRepository(
    transactions: List<NetTransaction> = emptyList(),
) : TransactionRepository {

    val transactions = MutableStateFlow(transactions)

    override suspend fun insert(transaction: NetTransaction): Long {
        this.transactions.update { it + transaction }
        return transaction.id
    }

    override suspend fun update(transaction: NetTransaction) {
        transactions.update { list -> list.map { if (it.id == transaction.id) transaction else it } }
    }

    override fun observeAll(): Flow<List<NetTransaction>> = transactions

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

fun aTransaction(
    id: Long = 1L,
    method: String = "GET",
    host: String = "api.example.com",
    path: String = "/users",
    responseCode: Int? = 200,
    durationMs: Long? = 50L,
    requestBodySize: Long = 0L,
    responseBodySize: Long = 0L,
    responseBody: String? = null,
    error: String? = null,
) = NetTransaction(
    id = id,
    requestDate = 0L,
    durationMs = durationMs,
    method = method,
    scheme = "https",
    host = host,
    path = path,
    query = null,
    requestHeaders = emptyList(),
    requestBody = null,
    requestContentType = null,
    requestBodySize = requestBodySize,
    responseCode = responseCode,
    responseBody = responseBody,
    responseBodySize = responseBodySize,
    error = error,
)

fun aMockRule(
    id: String = "rule",
    name: String = "Login",
    isEnabled: Boolean = true,
) = MockRule(id = id, name = name, urlPattern = "/login", isEnabled = isEnabled)
