package com.example.ytpost.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "telegram_chats")
data class TelegramChat(
    @PrimaryKey val chatId: Long,
    val title: String,
    val type: String, // "channel", "megagroup", "group"
    val username: String?,
    val participantsCount: Int?,
    val cachedAt: Long = System.currentTimeMillis()
)
