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
    version = 3,
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    // Debug tool: a downgraded host app may drop captured traffic rather than crash.
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }

        // ── Migrations ────────────────────────────────────────────────────────

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
    }
}
