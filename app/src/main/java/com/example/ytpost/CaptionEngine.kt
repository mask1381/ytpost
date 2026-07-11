package com.example.ytpost

import com.example.ytpost.data.DownloadPreferenceProfile
import org.json.JSONArray
import org.json.JSONObject

object CaptionEngine {

    fun process(title: String, url: String, pref: DownloadPreferenceProfile?): String {
        var caption = pref?.captionTemplate ?: "{title}\n{url}"
        
        // 1. Initial replacement of basic placeholders
        caption = caption.replace("{title}", title).replace("{url}", url)

        // 2. Apply JSON rules if they exist
        pref?.captionRulesJson?.let { jsonStr ->
            try {
                val rules = JSONArray(jsonStr)
                for (i in 0 until rules.length()) {
                    val rule = rules.getJSONObject(i)
                    val find = rule.optString("find")
                    val replace = rule.optString("replace")
                    if (find.isNotEmpty()) {
                        caption = caption.replace(find, replace)
                    }
                }
            } catch (e: Exception) {
                AppLogger.logError("JSON Rule Error: ${e.message}")
            }
        }

        return caption
    }
}
