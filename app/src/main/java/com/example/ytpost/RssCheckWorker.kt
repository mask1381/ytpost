package com.example.ytpost

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ytpost.data.AppDatabase
import com.example.ytpost.data.ProcessedItem
import com.example.ytpost.data.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RssCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(applicationContext)
        val sharedPrefs = applicationContext.getSharedPreferences("rss_prefs", Context.MODE_PRIVATE)
        val rssUrls = sharedPrefs.getStringSet("rss_sources", emptySet()) ?: emptySet()

        for (url in rssUrls) {
            try {
                // TODO: Call Chaquopy to fetch RSS/Youtube channel items
                val newItems = fetchNewItemsFromPython(url) 

                for (itemUrl in newItems) {
                    if (!database.processedItemDao().isProcessed(itemUrl)) {
                        // Add to task queue
                        database.taskDao().insert(Task(sourceUrl = itemUrl, status = "queued"))
                        // Mark as processed
                        database.processedItemDao().insert(ProcessedItem(url = itemUrl))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        Result.success()
    }

    private fun fetchNewItemsFromPython(url: String): List<String> {
        // TODO: Actual Chaquopy call to a python script that parses RSS
        // For now, return empty list
        return emptyList()
    }
}
