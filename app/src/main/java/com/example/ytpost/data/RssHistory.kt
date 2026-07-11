package com.example.ytpost.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rss_history")
data class RssHistory(
    @PrimaryKey val videoId: String,
    val sourceUrl: String,
    val processedAt: Long = System.currentTimeMillis()
)
