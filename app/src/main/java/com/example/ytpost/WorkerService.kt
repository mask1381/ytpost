package com.example.ytpost

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.chaquo.python.Python
import com.example.ytpost.data.AppDatabase
import com.example.ytpost.data.Task
import kotlinx.coroutines.*
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
        sessionManager = TelegramSessionManager(this)
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
                val task = database.taskDao().getNextQueuedTask()
                if (task != null) {
                    processTask(task)
                } else {
                    delay(5000) // Wait for new tasks
                }
            }
        }
    }

    private suspend fun processTask(task: Task) {
        AppLogger.log("New Task: ${task.sourceUrl}")
        updateNotification("Processing: ${task.sourceUrl}")
        
        database.taskDao().update(task.copy(status = "downloading"))
        
        try {
            val py = Python.getInstance()
            val downloadDir = getExternalFilesDir("downloads")?.absolutePath ?: filesDir.absolutePath
            
            val downloader = py.getModule("downloader")
            val captionBuilder = py.getModule("caption_builder")
            val uploader = py.getModule("uploader")

            // 1. Download
            AppLogger.log("Downloading...")
            val downloadPyResult = downloader.callAttr("download_video", task.sourceUrl, downloadDir)
            val downloadList = downloadPyResult.asList()
            
            val statusOrPath = downloadList[0].toString()
            if (statusOrPath == "ERROR") {
                throw Exception(downloadList[1].toString())
            }
            
            val filePath = statusOrPath
            val title = downloadList[1].toString()
            AppLogger.log("Download complete: $title")

            // 2. Build Caption
            val finalCaption = captionBuilder.callAttr("build_caption", title, task.sourceUrl).toString()
            
            // 3. Upload
            AppLogger.log("Uploading to Telegram...")
            database.taskDao().update(task.copy(status = "uploading"))
            
            val sessionStr = sessionManager.getSessionString()
            val apiId = sessionManager.getApiId()
            val apiHash = sessionManager.getApiHash()
            
            if (sessionStr == null || apiId == null || apiHash == null) {
                throw Exception("Telegram not configured.")
            }
            
            val uploadResult = uploader.callAttr(
                "upload_to_telegram", 
                sessionStr, 
                apiId, 
                apiHash, 
                filePath, 
                finalCaption,
                task.destination
            ).toString()
            
            if (uploadResult.startsWith("ERROR")) {
                throw Exception(uploadResult)
            }

            File(filePath).delete()
            database.taskDao().update(task.copy(status = "done"))
            AppLogger.log("Task finished successfully!")
            showCompletionNotification("Task Completed", "Finished processing ${task.sourceUrl}")
            
        } catch (e: Exception) {
            AppLogger.log("Task Failed: ${e.message}")
            database.taskDao().update(task.copy(status = "failed", errorMessage = e.message))
            showCompletionNotification("Task Failed", "Error: ${e.message}")
        }
    }

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
