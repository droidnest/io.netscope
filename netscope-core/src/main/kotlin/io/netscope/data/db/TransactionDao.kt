package io.netscope.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.netscope.model.TrafficSummary
import io.netscope.model.TransactionSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TransactionEntity): Long

    /** Updates every column except the network-phase timing ones, which [updateTiming] owns. */
    @Update(entity = TransactionEntity::class)
    suspend fun updateWithoutTiming(transaction: TransactionWithoutTiming)

    @Query("SELECT * FROM transactions ORDER BY requestDate DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    /** Reads no body and no header, so a change costs little however large the traffic is. */
    @Query(
        """
        SELECT id, requestDate, durationMs, method, host, path, query, requestBodySize,
            responseCode, responseBodySize, error
        FROM transactions ORDER BY requestDate DESC
        """
    )
    fun observeSummaries(): Flow<List<TransactionSummary>>

    /** Ids of the transactions whose URL, method, code or a body is LIKE [pattern], with `\` as the escape. */
    @Query(
        """
        SELECT id FROM transactions
        WHERE scheme || '://' || host || path || COALESCE('?' || query, '') LIKE :pattern ESCAPE '\'
            OR method LIKE :pattern ESCAPE '\'
            OR CAST(responseCode AS TEXT) LIKE :pattern ESCAPE '\'
            OR requestBody LIKE :pattern ESCAPE '\'
            OR responseBody LIKE :pattern ESCAPE '\'
        """
    )
    suspend fun idsMatching(pattern: String): List<Long>

    /** The request headers of every transaction: their stored JSON cannot be searched with LIKE. */
    @Query("SELECT id, requestHeaders FROM transactions")
    suspend fun requestHeaders(): List<RequestHeadersRow>

    /** Reads no body, so a change costs little however many transactions are stored. */
    @Query(
        """
        SELECT COUNT(*) AS requestCount,
            COALESCE(SUM(error IS NOT NULL OR responseCode >= 400), 0) AS errorCount,
            (SELECT responseCode FROM transactions ORDER BY requestDate DESC LIMIT 1) AS lastResponseCode
        FROM transactions
        """
    )
    fun observeSummary(): Flow<TrafficSummary>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    /** Updates only the network-phase timing columns for an existing transaction. */
    @Query(
        "UPDATE transactions SET dnsMs = :dnsMs, connectMs = :connectMs, " +
        "sslMs = :sslMs, ttfbMs = :ttfbMs WHERE id = :id"
    )
    suspend fun updateTiming(id: Long, dnsMs: Long?, connectMs: Long?, sslMs: Long?, ttfbMs: Long?)

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
}
