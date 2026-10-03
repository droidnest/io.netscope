package io.netscope.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import io.netscope.NetScopeConfig
import io.netscope.data.db.NetScopeDatabase
import io.netscope.data.repository.TransactionRepositoryImpl
import io.netscope.model.NetHeader
import io.netscope.model.NetTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransactionRepositoryImplTest {

    private lateinit var db: NetScopeDatabase
    private lateinit var repository: TransactionRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NetScopeDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = TransactionRepositoryImpl(
            db.transactionDao(),
            NetScopeConfig(maxTransactions = 2),
            Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    fun `response update does not wipe timing written earlier`() = runTest {
        // Given
        val id = repository.insert(transaction())
        repository.updateTiming(id, dnsMs = 1, connectMs = 2, sslMs = 3, ttfbMs = 4)

        // When
        repository.update(transaction(id = id).copy(responseCode = 204, responseDate = 2_000L))

        // Then
        val stored = repository.getById(id)!!
        assertEquals(204, stored.responseCode)
        assertEquals(2_000L, stored.responseDate)
        assertEquals(1L, stored.dnsMs)
        assertEquals(2L, stored.connectMs)
        assertEquals(3L, stored.sslMs)
        assertEquals(4L, stored.ttfbMs)
    }

    @Test
    fun `response update does not overwrite request fields`() = runTest {
        // Given
        val id = repository.insert(transaction(requestBody = "original"))

        // When
        repository.update(
            transaction(id = id, requestBody = "changed").copy(
                responseCode = 200,
                responseHeaders = listOf(NetHeader("X-Trace", "abc")),
            ),
        )

        // Then
        val stored = repository.getById(id)!!
        assertEquals("original", stored.requestBody)
        assertEquals(listOf(NetHeader("X-Trace", "abc")), stored.responseHeaders)
    }

    // ── observeSummaries ────────────────────────────────────────────────────────────

    @Test
    fun `list emission omits bodies but search matches body text`() = runTest {
        // Given
        repository.insert(transaction(requestDate = 1, responseBody = "needle in body"))
        repository.insert(transaction(requestDate = 2, path = "/other"))

        // When
        val all = repository.observeSummaries().first()
        val found = repository.observeSummaries("needle").first()

        // Then
        assertEquals(2, all.size)
        all.forEach {
            assertNull(it.requestBody)
            assertNull(it.responseBody)
        }
        assertEquals(1, found.size)
        assertEquals("/users", found.single().path)
        assertNull(found.single().responseBody)
    }

    @Test
    fun `search treats LIKE wildcards literally`() = runTest {
        // Given
        repository.insert(transaction(requestDate = 1, path = "/plain"))
        repository.insert(transaction(requestDate = 2, path = "/a_b", query = "x=50%"))

        // When
        val percent = repository.observeSummaries("%").first()
        val underscore = repository.observeSummaries("_").first()
        val backslash = repository.observeSummaries("\\").first()

        // Then
        assertEquals(listOf("/a_b"), percent.map { it.path })
        assertEquals(listOf("/a_b"), underscore.map { it.path })
        assertEquals(emptyList<NetTransaction>(), backslash)
    }

    @Test
    fun `search is case-insensitive on URL`() = runTest {
        // Given
        repository.insert(transaction(host = "api.example.com", path = "/Users", query = "page=1"))

        // When
        val result = repository.observeSummaries("HTTPS://API.EXAMPLE.COM/users?PAGE").first()

        // Then
        assertEquals(1, result.size)
    }

    @Test
    fun `search matches method status code and request headers`() = runTest {
        // Given
        val id = repository.insert(
            transaction(method = "DELETE", headers = listOf(NetHeader("X-Tenant", "acme"))),
        )
        repository.update(transaction(id = id).copy(responseCode = 418))

        // When / Then
        assertEquals(1, repository.observeSummaries("delete").first().size)
        assertEquals(1, repository.observeSummaries("418").first().size)
        assertEquals(1, repository.observeSummaries("acme").first().size)
        assertEquals(0, repository.observeSummaries("nomatch").first().size)
    }

    @Test
    fun `list is ordered newest first`() = runTest {
        // Given
        repository.insert(transaction(requestDate = 10, path = "/old"))
        repository.insert(transaction(requestDate = 20, path = "/new"))

        // When
        val result = repository.observeSummaries().first()

        // Then
        assertEquals(listOf("/new", "/old"), result.map { it.path })
    }

    @Test
    fun `search matches header values containing HTML-escaped characters`() = runTest {
        // Given
        repository.insert(
            transaction(
                requestDate = 1,
                path = "/json",
                headers = listOf(NetHeader("Content-Type", "application/json; charset=utf-8")),
            ),
        )
        repository.insert(
            transaction(
                requestDate = 2,
                path = "/quoted",
                headers = listOf(NetHeader("X-Filter", "a=1&b='x' \"y\" <z>")),
            ),
        )

        // When / Then
        assertEquals(listOf("/json"), repository.observeSummaries("charset=utf-8").first().map { it.path })
        assertEquals(listOf("/json"), repository.observeSummaries("Content-Type: application").first().map { it.path })
        assertEquals(listOf("/quoted"), repository.observeSummaries("a=1&b='x'").first().map { it.path })
        assertEquals(listOf("/quoted"), repository.observeSummaries("\"y\" <z>").first().map { it.path })
    }

    @Test
    fun `search does not match JSON keys or flags of the header column`() = runTest {
        // Given
        repository.insert(transaction(headers = listOf(NetHeader("Authorization", "██", isRedacted = true))))

        // When / Then
        assertEquals(0, repository.observeSummaries("name").first().size)
        assertEquals(0, repository.observeSummaries("value").first().size)
        assertEquals(0, repository.observeSummaries("true").first().size)
        assertEquals(0, repository.observeSummaries("{").first().size)
        assertEquals(1, repository.observeSummaries("authorization").first().size)
    }

    @Test
    fun `headers round-trip without HTML escaping`() = runTest {
        // Given
        val headers = listOf(NetHeader("Content-Type", "text/html; charset=utf-8"), NetHeader("X-A", "<&'>"))

        // When
        val id = repository.insert(transaction(headers = headers))

        // Then
        assertEquals(headers, repository.getById(id)!!.requestHeaders)
        db.openHelper.readableDatabase
            .query("SELECT requestHeaders FROM transactions WHERE id = ?", arrayOf<Any>(id))
            .use { cursor ->
                cursor.moveToFirst()
                val json = cursor.getString(0)
                assertTrue(json, "charset=utf-8" in json && "<&'>" in json)
            }
    }

    // ── observeAll (deprecated) ───────────────────────────────────────────────

    @Suppress("DEPRECATION")
    @Test
    fun `deprecated observeAll emits full rows with bodies newest first`() = runTest {
        // Given
        repository.insert(transaction(requestDate = 1, path = "/old", requestBody = "req-old"))
        val id = repository.insert(transaction(requestDate = 2, path = "/new", requestBody = "req-new"))
        repository.update(transaction(id = id, requestDate = 2).copy(responseBody = "res-new"))

        // When
        val result = repository.observeAll().first()

        // Then
        assertEquals(listOf("/new", "/old"), result.map { it.path })
        assertEquals(listOf("req-new", "req-old"), result.map { it.requestBody })
        assertEquals("res-new", result.first().responseBody)
    }

    // ── getByIds ──────────────────────────────────────────────────────────────

    @Test
    fun `getByIds returns only requested rows with bodies newest first`() = runTest {
        // Given
        repository.config = NetScopeConfig(maxTransactions = 10)
        val a = repository.insert(transaction(requestDate = 1, path = "/a", requestBody = "a"))
        repository.insert(transaction(requestDate = 2, path = "/b", requestBody = "b"))
        val c = repository.insert(transaction(requestDate = 3, path = "/c", requestBody = "c"))

        // When
        val result = repository.getByIds(listOf(a, c, 9_999L))

        // Then
        assertEquals(listOf("/c", "/a"), result.map { it.path })
        assertEquals(listOf("c", "a"), result.map { it.requestBody })
    }

    @Test
    fun `getByIds handles more ids than SQLite's variable limit`() = runTest {
        // Given
        repository.config = NetScopeConfig(maxTransactions = 1_500)
        val ids = (1..1_200).map { i -> repository.insert(transaction(requestDate = i.toLong())) }

        // When
        val result = repository.getByIds(ids + ids.first())

        // Then
        assertEquals(1_200, result.size)
        assertEquals((1_200 downTo 1).map { it.toLong() }, result.map { it.requestDate })
    }

    // ── updateResponseSize ────────────────────────────────────────────────────

    @Test
    fun `updateResponseSize changes only the response body size`() = runTest {
        // Given
        val id = repository.insert(transaction(requestBody = "req"))
        repository.update(transaction(id = id).copy(responseCode = 200, responseBody = "res", responseBodySize = 3))
        val before = repository.getById(id)!!

        // When
        repository.updateResponseSize(id, 4_096L)

        // Then
        assertEquals(before.copy(responseBodySize = 4_096L), repository.getById(id))
    }

    // ── observeById ───────────────────────────────────────────────────────────

    @Test
    fun `observeById re-emits when the response lands`() = runTest {
        // Given
        val id = repository.insert(transaction())

        repository.observeById(id).test {
            val pending = awaitItem()!!
            assertNull(pending.responseCode)

            // When
            repository.update(transaction(id = id).copy(responseCode = 200, responseBody = "ok"))

            // Then
            val done = awaitItem()!!
            assertEquals(200, done.responseCode)
            assertEquals("ok", done.responseBody)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeById emits null for unknown id`() = runTest {
        // Given / When / Then
        repository.observeById(42).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── insert / prune ────────────────────────────────────────────────────────

    @Test
    fun `insert prunes to maxTransactions keeping newest`() = runTest {
        // Given
        repository.insert(transaction(requestDate = 1, path = "/1"))
        repository.insert(transaction(requestDate = 2, path = "/2"))

        // When
        repository.insert(transaction(requestDate = 3, path = "/3"))

        // Then
        assertEquals(listOf("/3", "/2"), repository.getAllWithBodies().map { it.path })
    }

    @Test
    fun `config change is honoured by the next prune`() = runTest {
        // Given
        repository.config = NetScopeConfig(maxTransactions = 1)
        repository.insert(transaction(requestDate = 1, path = "/1"))

        // When
        repository.insert(transaction(requestDate = 2, path = "/2"))

        // Then
        assertEquals(listOf("/2"), repository.getAllWithBodies().map { it.path })
    }

    // ── getAllWithBodies / clearAll ───────────────────────────────────────────

    @Test
    fun `getAllWithBodies returns bodies`() = runTest {
        // Given
        val id = repository.insert(transaction(requestBody = "req"))
        repository.update(transaction(id = id).copy(responseBody = "res"))

        // When
        val result = repository.getAllWithBodies()

        // Then
        assertEquals("req", result.single().requestBody)
        assertEquals("res", result.single().responseBody)
    }

    @Test
    fun `clearAll removes every transaction`() = runTest {
        // Given
        repository.insert(transaction())

        // When
        repository.clearAll()

        // Then
        assertEquals(emptyList<NetTransaction>(), repository.getAllWithBodies())
        assertEquals(emptyList<NetTransaction>(), repository.observeSummaries().first())
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun transaction(
        id: Long = 0,
        requestDate: Long = 1_000L,
        method: String = "GET",
        host: String = "api.example.com",
        path: String = "/users",
        query: String? = null,
        headers: List<NetHeader> = emptyList(),
        requestBody: String? = null,
        responseBody: String? = null,
    ) = NetTransaction(
        id = id,
        requestDate = requestDate,
        method = method,
        scheme = "https",
        host = host,
        path = path,
        query = query,
        requestHeaders = headers,
        requestBody = requestBody,
        requestContentType = null,
        requestBodySize = 0L,
        responseBody = responseBody,
    )
}
