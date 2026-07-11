package com.example.ytpost.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rss_feeds")
data class RssFeed(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val channelName: String,
    val feedUrl: String,
    val isActive: Boolean = true,
    val lastCheckedItemId: String? = null,
    val captionScript: String? = null
)
