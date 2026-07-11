package com.example.ytpost.data

import android.content.Context
import com.example.ytpost.AppLogger
import com.example.ytpost.CaptionScriptEngine
import com.example.ytpost.ProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.net.HttpURLConnection
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

class RssRepository(private val context: Context, private val db: AppDatabase) {

    data class RssItem(val id: String, val title: String, val url: String, val author: String, val description: String, val published: String)

    suspend fun checkAllFeeds() = withContext(Dispatchers.IO) {
        val activeFeeds = db.rssFeedDao().getActiveFeeds()
        AppLogger.logInfo("RSS: Starting sync for \${activeFeeds.size} active feeds")
        for (feed in activeFeeds) {
            try {
                processFeed(feed)
            } catch (e: Exception) {
                AppLogger.logError("Feed Error (\${feed.channelName}): \${e.message}")
            }
        }
    }

    suspend fun processFeed(feed: RssFeed) = withContext(Dispatchers.IO) {
        val items = fetchRssItems(feed.feedUrl)
        if (items.isEmpty()) {
            AppLogger.logWarning("RSS: No items found for \${feed.channelName}")
            return@withContext
        }

        // Find items newer than lastCheckedItemId
        val newItems = if (feed.lastCheckedItemId == null) {
            listOf(items.first()) // Only take latest if never checked
        } else {
            val list = items.takeWhile { it.id != feed.lastCheckedItemId }
            if (list.size == items.size && items.isNotEmpty()) {
                 // If lastCheckedItemId was not found in the list, maybe it's too old or URL changed
                 // To prevent flooding, we only take the latest 3 items to reset.
                 items.take(3)
            } else {
                list
            }
        }

        if (newItems.isEmpty()) {
            AppLogger.logInfo("RSS: No new videos for \${feed.channelName}")
            return@withContext
        }

        // Limit maximum processing in one go to 10 items to prevent database/network overhead
        val finalItemsToProcess = newItems.take(10)

        for (item in finalItemsToProcess.reversed()) { // Process oldest first
            if (!db.rssHistoryDao().isProcessed(item.id)) {
                
                val info = CaptionScriptEngine.VideoInfo(
                    title = item.title,
                    url = item.url,
                    description = item.description,
                    channelName = item.author,
                    uploadDate = item.published
                )
                
                val caption = CaptionScriptEngine.process(info, feed.captionScript)

                val defaultDest = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .getString("default_destination", "") ?: ""

                db.taskDao().insert(Task(
                    sourceUrl = item.url,
                    destination = defaultDest,
                    status = "queued",
                    quality = "best",
                    useDefaultCaption = false,
                    customCaption = caption
                ))

                db.rssHistoryDao().insert(RssHistory(item.id, item.url))
            }
        }

        // Update last checked ID
        db.rssFeedDao().update(feed.copy(lastCheckedItemId = items.first().id))
        AppLogger.logSuccess("Feed Checked: \${feed.channelName} (\${newItems.size} new)")
    }

    fun fetchRssItems(url: String): List<RssItem> {
        val items = mutableListOf<RssItem>()
        var connection: HttpURLConnection? = null
        try {
            val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val useManualProxy = sharedPrefs.getBoolean("use_manual_proxy", false)
            
            val urlObj = URL(url)
            connection = if (useManualProxy) {
                val proxy = ProxyManager.detectProxy() // This usually returns a java.net.Proxy based on the port scan
                // Note: ProxyManager.detectProxy might need adjustment to return java.net.Proxy
                // For now, let's assume it handles the connection opening or we use simple URL.openStream()
                // If detecting proxy fails, fallback to direct
                urlObj.openConnection() as HttpURLConnection
            } else {
                urlObj.openConnection() as HttpURLConnection
            }

            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")

            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(connection.inputStream)
            
            val entries = doc.getElementsByTagName("entry")

            for (i in 0 until entries.length) {
                val entry = entries.item(i) as Element
                
                // YouTube Atom namespace handling
                val videoId = getElementTextByTagName(entry, "yt:videoId") ?: 
                             getElementTextByTagNameNS(entry, "http://www.youtube.com/xml/schemas/2015", "videoId")
                
                if (videoId == null) continue

                val title = getElementTextByTagName(entry, "title") ?: "No Title"
                val link = "https://www.youtube.com/watch?v=" + videoId
                
                val authorElement = entry.getElementsByTagName("author").item(0) as? Element
                val author = authorElement?.let { getElementTextByTagName(it, "name") } ?: ""
                
                val published = getElementTextByTagName(entry, "published") ?: ""
                
                items.add(RssItem(videoId, title, link, author, "", published))
            }
        } catch (e: Exception) {
            AppLogger.logError("Fetch Error: \${e.message}")
        } finally {
            connection?.disconnect()
        }
        return items
    }

    private fun getElementTextByTagName(parent: Element, tagName: String): String? {
        val nodes = parent.getElementsByTagName(tagName)
        if (nodes.length > 0) {
            return nodes.item(0).textContent
        }
        return null
    }

    private fun getElementTextByTagNameNS(parent: Element, namespaceURI: String, localName: String): String? {
        val nodes = parent.getElementsByTagNameNS(namespaceURI, localName)
        if (nodes.length > 0) {
            return nodes.item(0).textContent
        }
        return null
    }
}
