package io.netscope.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransactionEntity::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(HeaderListConverter::class)
abstract class NetScopeDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    companion object {
        private const val DB_NAME = "netscope.db"

        @Volatile
        private var instance: NetScopeDatabase? = null

        fun getInstance(context: Context): NetScopeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NetScopeDatabase::class.java,
                    DB_NAME,
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    // Debug tool: a downgraded host app may drop captured traffic rather than crash.
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }

        // ── Migrations ────────────────────────────────────────────────────────

        /** Every migration, oldest first; register all of them on any builder. */
        internal val ALL_MIGRATIONS: Array<Migration>
            get() = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        /** Adds the four network-phase timing columns (all nullable). */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN dnsMs     INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN connectMs INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN sslMs     INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN ttfbMs    INTEGER")
            }
        }

        /** Indexes `requestDate` (list ORDER BY + prune). Name matches Room's generated one. */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_requestDate` " +
                    "ON `transactions` (`requestDate`)"
                )
            }
        }

        /**
         * Adds the plain-text `requestHeadersText` search column and backfills it from the
         * `requestHeaders` JSON. The same pass re-encodes both header JSON columns without Gson's
         * HTML escaping (older rows store `=` as `\u003d`). A row whose JSON does not parse keeps
         * its JSON untouched and an empty search text.
         */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN requestHeadersText TEXT NOT NULL DEFAULT ''"
                )
                db.compileStatement(
                    "UPDATE transactions SET requestHeadersText = ?, requestHeaders = ?, " +
                    "responseHeaders = ? WHERE id = ?"
                ).use { update ->
                    db.query("SELECT id, requestHeaders, responseHeaders FROM transactions").use { cursor ->
                        while (cursor.moveToNext()) {
                            val requestJson = cursor.getString(1)
                            val responseJson = cursor.getString(2)
                            val request = runCatching { HeaderCodec.fromJson(requestJson) }.getOrNull()
                            val response = runCatching { HeaderCodec.fromJson(responseJson) }.getOrNull()
                            update.clearBindings()
                            update.bindString(1, request?.let(HeaderCodec::toSearchText).orEmpty())
                            update.bindString(2, request?.let(HeaderCodec::toJson) ?: requestJson)
                            update.bindString(3, response?.let(HeaderCodec::toJson) ?: responseJson)
                            update.bindLong(4, cursor.getLong(0))
                            update.executeUpdateDelete()
                        }
                    }
                }
            }
        }
    }
}
