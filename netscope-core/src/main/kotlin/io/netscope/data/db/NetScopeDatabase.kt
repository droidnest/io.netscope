package io.netscope.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.netscope.data.mapper.TransactionMapper

@Database(
    entities = [TransactionEntity::class],
    version = 4,
    exportSchema = false,
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    // Captured traffic is disposable: a DB with no migration path is rebuilt, not kept.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }

        // ── Migrations ────────────────────────────────────────────────────────

        /** Adds the four network-phase timing columns (all nullable). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN dnsMs     INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN connectMs INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN sslMs     INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN ttfbMs    INTEGER")
            }
        }

        /** Drops the rows too big for a CursorWindow, which older builds could store. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM transactions WHERE " +
                        "COALESCE(LENGTH(CAST(requestBody AS BLOB)), 0) + " +
                        "COALESCE(LENGTH(CAST(responseBody AS BLOB)), 0) > " +
                        2 * TransactionMapper.MAX_STORED_BODY_BYTES,
                )
            }
        }

        /** Indexes the column every list is sorted on and the pruning after each insert reads. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transactions_requestDate ON transactions (requestDate)",
                )
            }
        }
    }
}
