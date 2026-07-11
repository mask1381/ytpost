package com.example.ytpost

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ytpost.data.AppDatabase
import com.example.ytpost.data.RssRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RssWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isAutoEnabled = prefs.getBoolean("rss_auto_enabled", true)
        
        if (!isAutoEnabled) {
            return@withContext Result.success()
        }

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = RssRepository(applicationContext, database)
        
        try {
            repository.checkAllFeeds()
            Result.success()
        } catch (e: Exception) {
            AppLogger.logError("Worker Error: ${e.message}")
            Result.retry()
        }
    }
}
