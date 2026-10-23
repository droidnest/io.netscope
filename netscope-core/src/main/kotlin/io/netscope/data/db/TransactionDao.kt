package io.netscope.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

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
