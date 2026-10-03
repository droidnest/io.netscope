package io.netscope.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.netscope.NetScopeConfig
import io.netscope.data.db.NetScopeDatabase
import io.netscope.data.repository.TransactionRepositoryImpl
import io.netscope.model.NetHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migrates hand-built version-2 / version-3 databases (built with raw SQL so the test does not
 * depend on `MigrationTestHelper` and older schema JSONs). Room validates the migrated schema
 * against the current entities when it opens, so a wrong index name, column or default fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetScopeDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbFile get() = context.getDatabasePath(DB_NAME)

    @Before
    fun setUp() {
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `migration 2 to 3 adds requestDate index and keeps rows`() = runTest {
        // Given
        dbFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { v2 ->
            v2.execSQL(V2_CREATE_SQL)
            v2.execSQL(
                "INSERT INTO transactions (requestDate, method, scheme, host, path, " +
                "requestHeaders, requestBodySize, responseHeaders, responseBodySize, dnsMs) " +
                "VALUES (1000, 'GET', 'https', 'api.example.com', '/users', '[]', 0, '[]', 0, 7)"
            )
            v2.version = 2
        }

        // When
        val db = Room.databaseBuilder(context, NetScopeDatabase::class.java, DB_NAME)
            .addMigrations(*NetScopeDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        // Then
        try {
            val rows = db.transactionDao().getAll()
            assertEquals(1, rows.size)
            assertEquals(7L, rows.single().dnsMs)
            db.openHelper.readableDatabase
                .query("PRAGMA index_list(`transactions`)")
                .use { cursor ->
                    val names = buildList {
                        while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                    }
                    assertTrue(names.toString(), "index_transactions_requestDate" in names)
                }
        } finally {
            db.close()
        }
    }

    @Test
    fun `migration 3 to 4 backfills header search text from escaped JSON`() = runTest {
        // Given — headers exactly as the old default Gson() wrote them (`=` and `'` escaped)
        val escapedJson =
            "[{\"name\":\"Content-Type\",\"value\":\"application/json; charset\\u003dutf-8\",\"isRedacted\":false}," +
            "{\"name\":\"X-Note\",\"value\":\"it\\u0027s \\u003cok\\u003e\",\"isRedacted\":false}]"
        dbFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { v3 ->
            v3.execSQL(V2_CREATE_SQL)
            v3.execSQL(V3_INDEX_SQL)
            v3.execSQL(
                "INSERT INTO transactions (requestDate, method, scheme, host, path, " +
                "requestHeaders, requestBodySize, responseHeaders, responseBodySize) " +
                "VALUES (1000, 'GET', 'https', 'api.example.com', '/users', ?, 0, ?, 0)",
                arrayOf(escapedJson, escapedJson),
            )
            v3.execSQL(
                "INSERT INTO transactions (requestDate, method, scheme, host, path, " +
                "requestHeaders, requestBodySize, responseHeaders, responseBodySize) " +
                "VALUES (2000, 'GET', 'https', 'api.example.com', '/plain', '[]', 0, '[]', 0)"
            )
            v3.version = 3
        }

        // When
        val db = Room.databaseBuilder(context, NetScopeDatabase::class.java, DB_NAME)
            .addMigrations(*NetScopeDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        val repository = TransactionRepositoryImpl(db.transactionDao(), NetScopeConfig(), Dispatchers.Unconfined)

        // Then
        try {
            assertEquals(listOf("/users"), repository.observeSummaries("charset=utf-8").first().map { it.path })
            assertEquals(listOf("/users"), repository.observeSummaries("it's <ok>").first().map { it.path })
            assertEquals(0, repository.observeSummaries("isRedacted").first().size)
            assertEquals(2, repository.observeSummaries().first().size)

            val migrated = repository.getAllWithBodies().single { it.path == "/users" }
            val expected = listOf(
                NetHeader("Content-Type", "application/json; charset=utf-8"),
                NetHeader("X-Note", "it's <ok>"),
            )
            assertEquals(expected, migrated.requestHeaders)
            assertEquals(expected, migrated.responseHeaders)
            db.openHelper.readableDatabase
                .query("SELECT requestHeaders, responseHeaders FROM transactions WHERE path = '/users'")
                .use { cursor ->
                    cursor.moveToFirst()
                    assertTrue(cursor.getString(0), "charset=utf-8" in cursor.getString(0))
                    assertTrue(cursor.getString(1), "charset=utf-8" in cursor.getString(1))
                }
        } finally {
            db.close()
        }
    }

    private companion object {
        const val DB_NAME = "netscope-migration-test.db"

        /** The v2 table exactly as Room 2.x created it (v3 schema minus the index). */
        const val V2_CREATE_SQL =
            "CREATE TABLE IF NOT EXISTS `transactions` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `requestDate` INTEGER NOT NULL, " +
            "`responseDate` INTEGER, `durationMs` INTEGER, `dnsMs` INTEGER, `connectMs` INTEGER, " +
            "`sslMs` INTEGER, `ttfbMs` INTEGER, `method` TEXT NOT NULL, `scheme` TEXT NOT NULL, " +
            "`host` TEXT NOT NULL, `path` TEXT NOT NULL, `query` TEXT, " +
            "`requestHeaders` TEXT NOT NULL, `requestBody` TEXT, `requestContentType` TEXT, " +
            "`requestBodySize` INTEGER NOT NULL, `responseCode` INTEGER, `responseMessage` TEXT, " +
            "`responseHeaders` TEXT NOT NULL, `responseBody` TEXT, `responseContentType` TEXT, " +
            "`responseBodySize` INTEGER NOT NULL, `error` TEXT)"

        /** The only v3 addition over [V2_CREATE_SQL]. */
        const val V3_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `index_transactions_requestDate` ON `transactions` (`requestDate`)"
    }
}
