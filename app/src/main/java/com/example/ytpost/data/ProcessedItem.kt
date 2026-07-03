package com.example.ytpost.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "processed_items")
data class ProcessedItem(
    @PrimaryKey val url: String,
    val processedAt: Long = System.currentTimeMillis()
)
