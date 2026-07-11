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

class RssCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(applicationContext)
        val rssPrefs = applicationContext.getSharedPreferences("rss_prefs", Context.MODE_PRIVATE)
        val appPrefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        
        val defaultDest = appPrefs.getString("default_destination", "me") ?: "me"
        // Get channel IDs from preferences (saved by SettingsFragment)
        val channels = rssPrefs.getStringSet("rss_sources", emptySet()) ?: emptySet()

        for (channelId in channels) {
            try {
                // Determine RSS URL (assuming it's a YouTube Channel ID)
                val rssUrl = if (channelId.startsWith("http")) channelId 
                             else "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
                
                // Get settings for this source
                val pref = database.downloadPreferenceDao().getPreference("rss", channelId)
                
                val items = fetchRssItems(rssUrl)
                for (item in items) {
                    val alreadyProcessed = database.rssHistoryDao().isProcessed(item.id)
                    
                    if (!alreadyProcessed) {
                        // Generate caption using the new Python builder
                        val caption = generateCaption(item.title)
                        
                        database.taskDao().insert(Task(
                            sourceUrl = item.url, 
                            destination = defaultDest, 
                            status = "queued",
                            quality = pref?.defaultQuality ?: "best",
                            onlyFirstItem = !(pref?.includeCarousel ?: true),
                            mediaFilter = pref?.allowedMediaTypes,
                            useDefaultCaption = true,
                            customCaption = caption
                        ))
                        
                        database.rssHistoryDao().insert(RssHistory(videoId = item.id, sourceUrl = item.url))
                        AppLogger.log("RSS: New video found and queued: ${item.title}")
                    }
                }
            } catch (e: Exception) {
                AppLogger.log("RSS Worker Error for $channelId: ${e.message}")
            }
        }

        Result.success()
    }

    private fun fetchRssItems(url: String): List<RssItem> {
        val items = mutableListOf<RssItem>()
        try {
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items
    }

    private fun generateCaption(title: String): String {
        return try {
            val py = Python.getInstance()
            val module = py.getModule("caption_builder")
            module.callAttr("build_caption", title).toString()
        } catch (e: Exception) {
            title
        }
    }

    data class RssItem(val id: String, val title: String, val url: String)
}
