package com.example.ytpost

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chaquo.python.Python
import com.example.ytpost.data.AppDatabase
import com.example.ytpost.data.RssHistory
import com.example.ytpost.data.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

class RssWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isAutoEnabled = prefs.getBoolean("rss_auto_enabled", true)
        
        if (!isAutoEnabled) {
            AppLogger.log("RSS: Auto-fetching is disabled. Skipping worker.")
            return@withContext Result.success()
        }

        val database = AppDatabase.getDatabase(applicationContext)
        val rssSources = database.downloadPreferenceDao().getAll().filter { it.sourceType == "rss" }

        for (source in rssSources) {
            val channelId = source.sourceIdentifier ?: continue
            val rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
            
            try {
                val videos = fetchRssItems(rssUrl)
                for (video in videos) {
                    if (!database.rssHistoryDao().isProcessed(video.id)) {
                        // New video found!
                        processNewVideo(video, source.defaultQuality, source.useDefaultCaption, database)
                        database.rssHistoryDao().insert(RssHistory(video.id, video.url))
                    }
                }
            } catch (e: Exception) {
                AppLogger.log("RSS Error for $channelId: ${e.message}")
            }
        }

        Result.success()
    }

    private fun fetchRssItems(url: String): List<RssItem> {
        val items = mutableListOf<RssItem>()
        val conn = URL(url).openConnection()
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(conn.getInputStream())
        val entries = doc.getElementsByTagName("entry")

        for (i in 0 until entries.length) {
            val entry = entries.item(i) as Element
            val id = entry.getElementsByTagName("yt:videoId").item(0)?.textContent ?: continue
            val title = entry.getElementsByTagName("title").item(0)?.textContent ?: "No Title"
            val link = "https://www.youtube.com/watch?v=$id"
            items.add(RssItem(id, title, link))
        }
        return items
    }

    private suspend fun processNewVideo(item: RssItem, quality: String, autoCaption: Boolean, db: AppDatabase) {
        // Attempt to find specific preferences for this source
        val channelId = item.url.substringAfter("channel_id=", item.url.substringAfter("v=", ""))
        val pref = db.downloadPreferenceDao().getPreference("rss", channelId)

        val py = Python.getInstance()
        val builder = py.getModule("caption_builder")
        
        var caption: String? = null
        if (autoCaption) {
            caption = CaptionEngine.process(item.title, item.url, pref)
        }

        db.taskDao().insert(Task(
            sourceUrl = item.url,
            destination = "", // Will be filled by WorkerService or default
            status = "queued",
            quality = pref?.defaultQuality ?: quality,
            useDefaultCaption = autoCaption,
            customCaption = caption
        ))
        
        AppLogger.log("RSS: Added new video to queue: ${item.title}")
    }

    data class RssItem(val id: String, val title: String, val url: String)
}
