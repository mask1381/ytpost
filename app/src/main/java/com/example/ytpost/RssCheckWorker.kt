package com.example.ytpost

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chaquo.python.Python
import com.example.ytpost.data.AppDatabase
import com.example.ytpost.data.RssRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

class RssCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = RssRepository(applicationContext, database)
        
        try {
            repository.checkAllFeeds()
            Result.success()
        } catch (e: Exception) {
            AppLogger.logError("RSS Worker Error: ${e.message}")
            Result.retry()
        }
    }
}
