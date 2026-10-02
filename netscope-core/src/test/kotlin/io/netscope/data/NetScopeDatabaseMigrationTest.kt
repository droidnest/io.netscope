package io.netscope.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.netscope.data.db.NetScopeDatabase
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
 * Migrates a hand-built version-2 database (only the v3 schema JSON is exported, so
 * `MigrationTestHelper` cannot create v2). Room validates the migrated schema against the
 * v3 entities when it opens, so a wrong index name or column fails here.
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
            .addMigrations(NetScopeDatabase.MIGRATION_1_2, NetScopeDatabase.MIGRATION_2_3)
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
    }
}
