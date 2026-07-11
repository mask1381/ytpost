package com.example.ytpost.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RssFeedDao {
    @Query("SELECT * FROM rss_feeds")
    fun getAllFeeds(): Flow<List<RssFeed>>

    @Query("SELECT * FROM rss_feeds WHERE isActive = 1")
    suspend fun getActiveFeeds(): List<RssFeed>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feed: RssFeed): Long

    @Update
    suspend fun update(feed: RssFeed)

    @Delete
    suspend fun delete(feed: RssFeed)

    @Query("SELECT * FROM rss_feeds WHERE id = :id")
    suspend fun getFeedById(id: Long): RssFeed?
}
