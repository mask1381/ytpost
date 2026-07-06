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
        val rssPrefs = applicationContext.getSharedPreferences("rss_prefs", Context.MODE_PRIVATE)
        val appPrefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        
        val defaultDest = appPrefs.getString("default_destination", "me") ?: "me"
        val rssUrls = rssPrefs.getStringSet("rss_sources", emptySet()) ?: emptySet()

        for (url in rssUrls) {
            try {
                // دریافت تنظیمات اختصاصی برای این فید
                val pref = database.downloadPreferenceDao().getPreference("rss", url)
                
                val urlHash = url.hashCode().toString()
                val isFirstCheck = !rssPrefs.getBoolean("is_first_check_done_$urlHash", false)
                
                val newItems = fetchNewItemsFromPython(url) 

                for (itemUrl in newItems) {
                    val alreadyProcessed = database.processedItemDao().isProcessed(itemUrl)
                    
                    if (!alreadyProcessed) {
                        if (!isFirstCheck) {
                            // استفاده از تنظیمات ذخیره شده یا مقادیر پیش‌فرض
                            database.taskDao().insert(Task(
                                sourceUrl = itemUrl, 
                                destination = defaultDest, 
                                status = "queued",
                                quality = pref?.defaultQuality ?: "best",
                                onlyFirstItem = !(pref?.includeCarousel ?: true),
                                mediaFilter = pref?.allowedMediaTypes,
                                useDefaultCaption = pref?.useDefaultCaption ?: true
                            ))
                        }
                        database.processedItemDao().insert(ProcessedItem(url = itemUrl))
                    }
                }
                
                if (isFirstCheck) {
                    rssPrefs.edit().putBoolean("is_first_check_done_$urlHash", true).apply()
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
