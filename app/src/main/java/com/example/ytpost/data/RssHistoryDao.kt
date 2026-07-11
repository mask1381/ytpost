package com.example.ytpost.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RssHistoryDao {
    @Query("SELECT EXISTS(SELECT 1 FROM rss_history WHERE videoId = :videoId LIMIT 1)")
    suspend fun isProcessed(videoId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: RssHistory)
    
    @Query("DELETE FROM rss_history WHERE processedAt < :timestamp")
    suspend fun clearOldHistory(timestamp: Long)
}
