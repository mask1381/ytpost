package com.example.ytpost.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Query("SELECT * FROM tasks WHERE status = 'queued' OR (status = 'failed' AND retryCount < 3 AND :currentTime - lastRetryTimestamp > 30000) LIMIT 1")
    suspend fun getNextQueuedTask(currentTime: Long = System.currentTimeMillis()): Task?

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: Long): Task?

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun delete(taskId: Long)
}
