package com.example.ytpost.data

import androidx.room.*

@Dao
interface ProcessedItemDao {
    @Query("SELECT EXISTS(SELECT 1 FROM processed_items WHERE url = :url)")
    suspend fun isProcessed(url: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ProcessedItem)
}
