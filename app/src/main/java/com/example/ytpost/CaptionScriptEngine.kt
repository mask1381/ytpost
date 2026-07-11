package com.example.ytpost

import app.cash.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

object CaptionScriptEngine {

    data class VideoInfo(
        val title: String,
        val url: String,
        val description: String,
        val channelName: String,
        val uploadDate: String
    )

    const val DEFAULT_SCRIPT = """
const hashtags = extractHashtags(videoInfo.title);
const cleanTitle = videoInfo.title.replace(/#\w+/g, '').trim();
return "🎬 <b>" + cleanTitle + "</b>\n\n" + hashtags + "\n\n" + videoInfo.url + "\n\n🌸 <a>" + videoInfo.channelName + "</a>";
    """

    suspend fun process(info: VideoInfo, script: String?): String = withContext(Dispatchers.Default) {
        val finalScript = if (script.isNullOrBlank()) DEFAULT_SCRIPT else script
        
        try {
            withTimeout(3000) {
                QuickJs.create().use { context ->
                    // 1. Setup helpers
                    context.evaluate("""
                        function extractHashtags(text) {
                            var matches = text.match(/#\w+/g);
                            return matches ? matches.join(' ') : '';
                        }
                    """.trimIndent())

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
