package com.example.ytpost

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chaquo.python.Python
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
                val newItems = fetchNewItemsFromPython(url) 

                for (itemUrl in newItems) {
                    if (!database.processedItemDao().isProcessed(itemUrl)) {
                        // افزودن به صف تسک‌ها
                        database.taskDao().insert(Task(sourceUrl = itemUrl, status = "queued"))
                        // علامت‌گذاری به عنوان پردازش شده
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
        return try {
            val py = Python.getInstance()
            val module = py.getModule("rss_checker")
            val result = module.callAttr("fetch_new_items", url)
            result.asList().map { it.toString() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
