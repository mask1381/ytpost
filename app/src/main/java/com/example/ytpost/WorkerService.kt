package com.example.ytpost

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.ytpost.data.AppDatabase
import com.example.ytpost.data.Task
import kotlinx.coroutines.*

class WorkerService : Service() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var database: AppDatabase

    companion object {
        const val CHANNEL_ID = "WorkerServiceChannel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Starting worker...")
        startForeground(NOTIFICATION_ID, notification)

        processQueue()

        return START_STICKY
    }

    private fun processQueue() {
        serviceScope.launch(Dispatchers.IO) {
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
        updateNotification("Processing: ${task.sourceUrl}")
        
        // Update status to downloading
        database.taskDao().update(task.copy(status = "downloading"))
        
        try {
            // TODO: Call Chaquopy to run yt-dlp script
            delay(2000) // Simulating work
            
            // Update status to uploading
            database.taskDao().update(task.copy(status = "uploading"))
            
            // TODO: Call Chaquopy to run Telethon script
            delay(2000) // Simulating work
            
            // Update status to done
            database.taskDao().update(task.copy(status = "done"))
            showCompletionNotification("Task Completed", "Finished processing ${task.sourceUrl}")
        } catch (e: Exception) {
            database.taskDao().update(task.copy(status = "failed", errorMessage = e.message))
            showCompletionNotification("Task Failed", "Error processing ${task.sourceUrl}")
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("YTPost Worker")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
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
        serviceJob.cancel()
    }
}
