package com.example.ytpost

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.chaquo.python.Python
import com.example.ytpost.data.AppDatabase
import com.example.ytpost.data.Task
import kotlinx.coroutines.*
import org.json.JSONArray
import java.io.File

class WorkerService : Service() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var database: AppDatabase
    private lateinit var sessionManager: TelegramSessionManager

    companion object {
        const val CHANNEL_ID = "WorkerServiceChannel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        sessionManager = TelegramSessionManager.getInstance(this)
        createNotificationChannel()
        AppLogger.log("WorkerService Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Starting worker...")
        startForeground(NOTIFICATION_ID, notification)

        processQueue()

        return START_STICKY
    }

    private fun processQueue() {
        serviceScope.launch(Dispatchers.IO) {
            AppLogger.log("Queue processing started")
            while (isActive) {
                val task = database.taskDao().getNextQueuedTask(System.currentTimeMillis())
                if (task != null) {
                    processTask(task)
                } else {
                    delay(10000)
                }
            }
        }
    }

    private fun createProgressListener(task: Task) = object : ProgressListener {
        private var lastUpdate = 0L
        override fun onProgress(progress: Int) {
            val now = System.currentTimeMillis()
            // Throttling updates to database to avoid excessive IO
            if (now - lastUpdate > 1000) { 
                lastUpdate = now
                serviceScope.launch(Dispatchers.IO) {
                    database.taskDao().update(task.copy(progress = progress))
                }
            }
        }
    }

    private suspend fun processTask(task: Task) {
        AppLogger.log("Processing Task: ${task.sourceUrl} (Attempt: ${task.retryCount + 1})")
        updateNotification("Processing: ${task.sourceUrl}")
        
        database.taskDao().update(task.copy(status = "downloading", progress = 0))
        
        var currentProxy = ProxyManager.detectProxy()
        
        try {
            executeTaskLogic(task, currentProxy)
        } catch (e: RecoverableException) {
            val errorMsg = e.message ?: ""
            // Fallback logic: if failed with proxy/connection error, try once without proxy immediately
            if (currentProxy != null && (errorMsg.contains("proxy", ignoreCase = true) || 
                errorMsg.contains("connection", ignoreCase = true) || 
                errorMsg.contains("reloaded", ignoreCase = true))) {
                
                AppLogger.log("Task failed with proxy. Retrying immediately without proxy as fallback...")
                try {
                    executeTaskLogic(task, null)
                } catch (e2: Exception) {
                    handleFatalError(task, e2)
                }
            } else {
                handleRecoverableError(task, e)
            }
        } catch (e: Exception) {
            handleFatalError(task, e)
        }
    }

    private suspend fun executeTaskLogic(task: Task, proxy: String?) {
        val py = Python.getInstance()
        val downloadDir = getExternalFilesDir("downloads")?.absolutePath ?: filesDir.absolutePath
        
        val downloader = py.getModule("downloader")
        val captionBuilder = py.getModule("caption_builder")
        val uploader = py.getModule("uploader")

        // 1. Download
        AppLogger.log("Downloading... (Proxy: ${proxy ?: "None"})")
        val ffmpegPath = FfmpegManager.getFfmpegPath(this@WorkerService)
        val progressListener = createProgressListener(task)
        
        val sharedPrefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val cookiePath = sharedPrefs.getString("cookie_file_path", null)
        
        val downloadPyResult = downloader.callAttr(
            "download_video", 
            task.sourceUrl, 
            downloadDir, 
            task.quality, 
            task.onlyFirstItem, 
            task.mediaFilter,
            cookiePath,
            ffmpegPath,
            proxy,
            progressListener,
            task.writeSubs,
            task.audioOnly,
            task.customArgs
        ).toString()
        
        val downloadListJson = JSONArray(downloadPyResult)
        
        if (downloadListJson.length() == 0) {
            throw Exception("No files downloaded.")
        }

        val firstItem = downloadListJson.getJSONArray(0)
        if (firstItem.getString(0) == "ERROR") {
            val errorMsg = firstItem.getString(1)
            if (isRetryableError(errorMsg)) {
                throw RecoverableException(errorMsg)
            } else {
                throw Exception(errorMsg)
            }
        }
        
        val filePaths = mutableListOf<String>()
        var firstTitle = ""
        for (i in 0 until downloadListJson.length()) {
            val item = downloadListJson.getJSONArray(i)
            filePaths.add(item.getString(0))
            if (i == 0) firstTitle = item.getString(1)
        }

        // 2. Build Caption
        val finalCaption = if (task.customCaption != null) {
            task.customCaption
        } else if (task.useDefaultCaption) {
            captionBuilder.callAttr("build_caption", firstTitle, task.sourceUrl).toString()
        } else {
            ""
        }
        
        // 3. Upload
        AppLogger.log("Uploading to Telegram...")
        database.taskDao().update(task.copy(status = "uploading", progress = 0))
        
        val sessionStr = sessionManager.getSessionString()
        val apiId = sessionManager.getApiId()
        val apiHash = sessionManager.getApiHash()
        
        if (sessionStr == null || apiId == null || apiHash == null) {
            throw Exception("Telegram not configured.")
        }
        
        val filePathsJson = JSONArray(filePaths).toString()
        
        val uploadResult = uploader.callAttr(
            "upload_to_telegram", 
            sessionStr, 
            apiId, 
            apiHash, 
            filePathsJson, 
            finalCaption,
            task.destination,
            proxy,
            progressListener
        ).toString()
        
        if (uploadResult.startsWith("ERROR")) {
            if (isRetryableError(uploadResult)) {
                throw RecoverableException(uploadResult)
            } else {
                throw Exception(uploadResult)
            }
        }

        // Success Cleanup
        for (path in filePaths) {
            File(path).delete()
        }
        
        database.taskDao().update(task.copy(status = "done", errorMessage = null, progress = 100))
        AppLogger.log("Task finished: ${task.sourceUrl}")
        showCompletionNotification("Task Completed", "Finished processing ${task.sourceUrl}")
    }

    private suspend fun handleRecoverableError(task: Task, e: Exception) {
        val nextRetry = task.retryCount + 1
        AppLogger.log("Recoverable Error: ${e.message}. Retry $nextRetry/3")
        database.taskDao().update(task.copy(
            status = "failed", 
            errorMessage = "Retryable: ${e.message}",
            retryCount = nextRetry,
            lastRetryTimestamp = System.currentTimeMillis()
        ))
    }

    private suspend fun handleFatalError(task: Task, e: Exception) {
        AppLogger.log("Fatal Error: ${e.message}")
        database.taskDao().update(task.copy(
            status = "failed", 
            errorMessage = e.message,
            retryCount = 99
        ))
        showCompletionNotification("Task Failed", "Error: ${e.message}")
    }

    private fun isRetryableError(error: String): Boolean {
        val lower = error.lowercase()
        return lower.contains("timeout") || 
               lower.contains("connection") || 
               lower.contains("network") || 
               lower.contains("proxy") || 
               lower.contains("socks") ||
               lower.contains("http error 5") ||
               lower.contains("try again") ||
               lower.contains("not configured") ||
               lower.contains("reloaded")
    }

    class RecoverableException(message: String) : Exception(message)

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("YTPost Worker")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(title: String, content: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Worker Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.log("WorkerService Destroyed")
        serviceJob.cancel()
    }
}
