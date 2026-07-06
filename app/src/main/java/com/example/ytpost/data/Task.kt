package com.example.ytpost.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceUrl: String,
    val destination: String,
    val status: String, // queued, downloading, uploading, done, failed
    val caption: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
    
    // New fields for preferences
    val quality: String = "best",
    val onlyFirstItem: Boolean = false,
    val mediaFilter: String? = null, // comma separated like "video,photo" or single
    val useDefaultCaption: Boolean = true,
    val customCaption: String? = null,
    val progress: Int = 0,
    
    // Retry logic
    val retryCount: Int = 0,
    val lastRetryTimestamp: Long = 0
)
