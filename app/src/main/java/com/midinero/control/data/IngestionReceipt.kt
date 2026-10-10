package com.midinero.control.data

import androidx.room.*

// Retained after an expense is deleted: replaying an acknowledged event must not resurrect it.
@Entity(tableName = "ingestion_receipts")
data class IngestionReceipt(@PrimaryKey val eventId: String)

@Dao
interface IngestionDao {
    @Query("SELECT EXISTS(SELECT 1 FROM ingestion_receipts WHERE eventId = :id)")
    suspend fun contains(id: String): Boolean
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(receipt: IngestionReceipt)
}
