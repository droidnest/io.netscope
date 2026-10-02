package io.netscope.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    // ── Writes ────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TransactionEntity): Long

    /** Writes only the response columns; timing columns are left untouched. */
    @Update(entity = TransactionEntity::class)
    suspend fun updateResponse(update: TransactionResponseUpdate)

    /** Updates only the network-phase timing columns for an existing transaction. */
    @Query(
        "UPDATE transactions SET dnsMs = :dnsMs, connectMs = :connectMs, " +
        "sslMs = :sslMs, ttfbMs = :ttfbMs WHERE id = :id"
    )
    suspend fun updateTiming(id: Long, dnsMs: Long?, connectMs: Long?, sslMs: Long?, ttfbMs: Long?)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    /**
     * Prunes oldest transactions when the count exceeds [maxCount].
     * Called after each insert to enforce the configured limit.
     */
    @Query(
        """
        DELETE FROM transactions WHERE id IN (
            SELECT id FROM transactions
            ORDER BY requestDate ASC
            LIMIT MAX(0, (SELECT COUNT(*) FROM transactions) - :maxCount)
        )
        """
    )
    suspend fun pruneOldest(maxCount: Int)

    // ── Reads ─────────────────────────────────────────────────────────────────

    /**
     * Newest-first list projection: both bodies are returned as NULL so an emission never
     * loads up to `maxTransactions × 2 × maxBodySize` of text.
     *
     * [pattern] is a LIKE fragment already escaped with `\` (see the repository); an empty
     * pattern matches every row. The search still runs over the bodies, inside SQLite.
     * LIKE is case-insensitive for ASCII.
     */
    @Query(
        """
        SELECT id, requestDate, responseDate, durationMs,
               dnsMs, connectMs, sslMs, ttfbMs,
               method, scheme, host, path, `query`,
               requestHeaders, NULL AS requestBody, requestContentType, requestBodySize,
               responseCode, responseMessage, responseHeaders, NULL AS responseBody,
               responseContentType, responseBodySize,
               error
        FROM transactions
        WHERE :pattern = ''
           OR (scheme || '://' || host || path || COALESCE('?' || `query`, '')) LIKE '%' || :pattern || '%' ESCAPE '\'
           OR method                       LIKE '%' || :pattern || '%' ESCAPE '\'
           OR CAST(responseCode AS TEXT)   LIKE '%' || :pattern || '%' ESCAPE '\'
           OR requestHeaders               LIKE '%' || :pattern || '%' ESCAPE '\'
           OR requestBody                  LIKE '%' || :pattern || '%' ESCAPE '\'
           OR responseBody                 LIKE '%' || :pattern || '%' ESCAPE '\'
        ORDER BY requestDate DESC
        """
    )
    fun observeList(pattern: String): Flow<List<TransactionEntity>>

    /** Full row (bodies included); re-emits whenever the table changes. */
    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    /** One-shot read of every row with bodies, newest-first. Exports only. */
    @Query("SELECT * FROM transactions ORDER BY requestDate DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}
