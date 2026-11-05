package io.netscope.fake

import io.netscope.domain.repository.MockRuleRepository
import io.netscope.domain.repository.TransactionRepository
import io.netscope.model.MockRule
import io.netscope.model.NetTransaction
import io.netscope.model.TrafficSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import okhttp3.Call
import okhttp3.Headers
import okhttp3.Headers.Companion.headersOf
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import java.lang.reflect.Proxy

class FakeTransactionRepository(
    transactions: List<NetTransaction> = emptyList(),
) : TransactionRepository {

    val transactions = MutableStateFlow(transactions)

    /** Last [updateTiming] TTFB per transaction id. */
    val ttfbs = mutableMapOf<Long, Long?>()

    override suspend fun insert(transaction: NetTransaction): Long {
        this.transactions.update { it + transaction }
        return transaction.id
    }

    override suspend fun update(transaction: NetTransaction) {
        transactions.update { list -> list.map { if (it.id == transaction.id) transaction else it } }
    }

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

    override suspend fun updateTiming(id: Long, dnsMs: Long?, connectMs: Long?, sslMs: Long?, ttfbMs: Long?) {
        ttfbs[id] = ttfbMs
    }

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

/** An [Interceptor.Chain] that answers with [respond]; only `request`, `proceed` and `call` work. */
fun aChain(
    request: Request,
    respond: (Request) -> Response = { aResponse(request = it) },
): Interceptor.Chain = proxyOf(Interceptor.Chain::class.java) { name, args ->
    when (name) {
        "request" -> request
        "proceed" -> respond(args[0] as Request)
        "call" -> aCall()
        else -> error("Not faked: $name")
    }
}

fun aCall(): Call = proxyOf(Call::class.java) { name, _ -> error("Not faked: $name") }

fun aRequest(url: String = "https://api.example.com/users", body: RequestBody? = null): Request =
    Request.Builder().url(url).method(if (body == null) "GET" else "POST", body).build()

fun aResponse(
    request: Request,
    body: ResponseBody = "".toResponseBody(),
    headers: Headers = headersOf(),
): Response = Response.Builder()
    .request(request)
    .protocol(Protocol.HTTP_1_1)
    .code(200)
    .message("OK")
    .headers(headers)
    .body(body)
    .build()

private inline fun <reified T> proxyOf(type: Class<T>, crossinline answer: (String, List<Any?>) -> Any?): T =
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.get(0)
            "toString" -> type.simpleName
            else -> answer(method.name, args?.toList().orEmpty())
        }
    } as T
