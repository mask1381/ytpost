package com.example.ytpost.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_preferences")
data class DownloadPreferenceProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: String, // "manual" or "rss"
    val sourceIdentifier: String?, // RSS feed URL or null/global for manual
    val defaultQuality: String = "best", // best, medium, worst
    val includeCarousel: Boolean = true,
    val allowedMediaTypes: String = "video,photo,audio", // comma-separated
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
