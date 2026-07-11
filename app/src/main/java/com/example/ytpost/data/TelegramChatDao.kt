package com.example.ytpost.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TelegramChatDao {
    @Query("SELECT * FROM telegram_chats ORDER BY title ASC")
    fun getAllChats(): Flow<List<TelegramChat>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chats: List<TelegramChat>)

    @Query("DELETE FROM telegram_chats")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM telegram_chats")
    suspend fun getCount(): Int
}
