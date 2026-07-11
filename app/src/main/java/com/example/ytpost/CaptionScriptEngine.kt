package com.example.ytpost

import android.content.Context
import app.cash.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

object CaptionScriptEngine {

    data class VideoInfo(
        val title: String,
        val url: String,
        val description: String,
        val channelName: String,
        val uploadDate: String
    )

    interface PersianDateHelper {
        fun convert(dateStr: String): String
    }

    interface NetworkHelper {
        fun get(urlStr: String): String
    }

    interface HtmlHelper {
        fun escape(text: String): String
    }

    const val BUILTIN_FALLBACK_SCRIPT = """
const hashtags = extractHashtags(videoInfo.title);
const cleanTitle = escapeHtml(videoInfo.title.replace(/#\w+/g, '').trim());
const pDate = toPersianDate(videoInfo.uploadDate);
return "🎬 <b>" + cleanTitle + "</b>\n\n📅 " + pDate + "\n\n" + hashtags + "\n\n" + videoInfo.url + "\n\n🌸 <a href=\"https://t.me/genshinworldsensei\">GWS | Teyvat Archive</a>";
    """

    fun getGlobalDefaultScript(context: Context): String {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getString("global_default_caption_script", null) ?: BUILTIN_FALLBACK_SCRIPT
    }

    suspend fun process(info: VideoInfo, script: String?): String = withContext(Dispatchers.IO) {
        val finalScript = if (script.isNullOrBlank()) BUILTIN_FALLBACK_SCRIPT else script
        
        try {
            withTimeout(5000) { // Increased timeout for fetch support
                QuickJs.create().use { context ->
                    // 1. Setup helpers
                    context.evaluate("""
                        function extractHashtags(text) {
                            var matches = text.match(/#\w+/g);
                            return matches ? matches.join(' ') : '';
                        }
                    """.trimIndent())

                    // Persian Date Helper
                    context.set("toPersianDate", PersianDateHelper::class.java, object : PersianDateHelper {
                        override fun convert(dateStr: String): String {
                            if (dateStr.isBlank()) return ""
                            return try {
                                val formats = listOf(
                                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
                                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
                                    SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                )
                                var date: Date? = null
                                for (fmt in formats) {
                                    try {
                                        date = fmt.parse(dateStr)
                                        if (date != null) break
                                    } catch (e: Exception) {}
                                }
                                
                                val finalDate = date ?: Date()
                                val cal = Calendar.getInstance()
                                cal.time = finalDate
                                
                                // Basic Jalali conversion: Year - 621, Month offset
                                // This is a simplified version.
                                val gy = cal.get(Calendar.YEAR)
                                val gm = cal.get(Calendar.MONTH) + 1
                                val gd = cal.get(Calendar.DAY_OF_MONTH)
                                
                                val py = gy - 621
                                "شمسی: $py/$gm/$gd"
                            } catch (e: Exception) { dateStr }
                        }
                    })

                    // Simple synchronous fetch for JS
                    context.set("fetch", NetworkHelper::class.java, object : NetworkHelper {
                        override fun get(urlStr: String): String {
                            return try {
                                val url = URL(urlStr)
                                val conn = url.openConnection() as HttpURLConnection
                                conn.requestMethod = "GET"
                                conn.connectTimeout = 3000
                                conn.readTimeout = 3000
                                conn.inputStream.bufferedReader().use { it.readText() }
                            } catch (e: Exception) { "Error: " + e.message }
                        }
                    })

                    // HTML Escape Helper
                    context.set("escapeHtml", HtmlHelper::class.java, object : HtmlHelper {
                        override fun escape(text: String): String {
                            return escapeHtml(text)
                        }
                    })

                    // 2. Setup videoInfo
                    context.evaluate("""
                        var videoInfo = {
                            title: ${escapeJsString(info.title)},
                            url: ${escapeJsString(info.url)},
                            description: ${escapeJsString(info.description)},
                            channelName: ${escapeJsString(info.channelName)},
                            uploadDate: ${escapeJsString(info.uploadDate)}
                        };
                    """.trimIndent())

                    // 3. Execute script
                    val result = context.evaluate("(function(){ $finalScript })()") as? String
                    
                    if (result.isNullOrBlank()) {
                        fallbackCaption(info)
                    } else {
                        truncateHtmlSafe(result, 1024)
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.logError("Script Error: ${e.message}")
            fallbackCaption(info)
        }
    }

    private fun escapeJsString(s: String): String {
        return "\"" + s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""
    }

    private fun fallbackCaption(info: VideoInfo): String {
        return "🎬 <b>${escapeHtml(info.title)}</b>\n\n${info.url}\n\n🌸 ${escapeHtml(info.channelName)}"
    }

    fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    fun truncateHtmlSafe(html: String, maxLen: Int): String {
        if (html.length <= maxLen) return html

        var count = 0
        val truncated = StringBuilder()
        val openTags = mutableListOf<String>()
        
        var i = 0
        while (i < html.length && count < maxLen - 10) {
            val char = html[i]
            if (char == '<') {
                val end = html.indexOf('>', i)
                if (end != -1) {
                    val tagStr = html.substring(i + 1, end)
                    if (tagStr.startsWith("/")) {
                        if (openTags.isNotEmpty()) openTags.removeAt(openTags.size - 1)
                    } else if (!tagStr.endsWith("/")) {
                        val tagName = tagStr.split(" ")[0].lowercase()
                        openTags.add(tagName)
                    }
                    truncated.append(html.substring(i, end + 1))
                    i = end + 1
                    continue
                }
            }
            truncated.append(char)
            count++
            i++
        }

        if (i < html.length) truncated.append("...")

        for (j in openTags.size - 1 downTo 0) {
            truncated.append("</").append(openTags[j]).append(">")
        }

        return truncated.toString()
    }
}
