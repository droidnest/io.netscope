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

    // ── observeAll ────────────────────────────────────────────────────────────

    @Test
    fun `list emission omits bodies but search matches body text`() = runTest {
        // Given
        repository.insert(transaction(requestDate = 1, responseBody = "needle in body"))
        repository.insert(transaction(requestDate = 2, path = "/other"))

        // When
        val all = repository.observeAll().first()
        val found = repository.observeAll("needle").first()

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
        val percent = repository.observeAll("%").first()
        val underscore = repository.observeAll("_").first()
        val backslash = repository.observeAll("\\").first()

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
        val result = repository.observeAll("HTTPS://API.EXAMPLE.COM/users?PAGE").first()

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
        assertEquals(1, repository.observeAll("delete").first().size)
        assertEquals(1, repository.observeAll("418").first().size)
        assertEquals(1, repository.observeAll("acme").first().size)
        assertEquals(0, repository.observeAll("nomatch").first().size)
    }

    @Test
    fun `list is ordered newest first`() = runTest {
        // Given
        repository.insert(transaction(requestDate = 10, path = "/old"))
        repository.insert(transaction(requestDate = 20, path = "/new"))

        // When
        val result = repository.observeAll().first()

        // Then
        assertEquals(listOf("/new", "/old"), result.map { it.path })
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
        assertEquals(emptyList<NetTransaction>(), repository.observeAll().first())
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
