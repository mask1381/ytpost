package com.example.ytpost

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.example.ytpost.data.AppDatabase
import com.example.ytpost.data.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private lateinit var sessionManager: TelegramSessionManager
    private lateinit var taskAdapter: TaskAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = AppDatabase.getDatabase(this)
        sessionManager = TelegramSessionManager(this)

        setupUI()
        startWorkerService()
        scheduleRssWorker()
        observeTasks()
    }

    private fun setupUI() {
        val etApiId = findViewById<EditText>(R.id.etApiId)
        val etApiHash = findViewById<EditText>(R.id.etApiHash)
        val etPhone = findViewById<EditText>(R.id.etPhone)
        val btnSaveConfig = findViewById<Button>(R.id.btnSaveConfig)

        // Pre-fill if exists
        etApiId.setText(sessionManager.getApiId())
        etApiHash.setText(sessionManager.getApiHash())
        etPhone.setText(sessionManager.getPhoneNumber())

        btnSaveConfig.setOnClickListener {
            sessionManager.saveTelegramCredentials(
                etApiId.text.toString(),
                etApiHash.text.toString(),
                etPhone.text.toString()
            )
            Toast.makeText(this, "Config Saved", Toast.LENGTH_SHORT).show()
        }

        val etManualLink = findViewById<EditText>(R.id.etManualLink)
        val btnSendLink = findViewById<Button>(R.id.btnSendLink)

        btnSendLink.setOnClickListener {
            val link = etManualLink.text.toString()
            if (link.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    database.taskDao().insert(Task(sourceUrl = link, status = "queued"))
                    withContext(Dispatchers.Main) {
                        etManualLink.setText("")
                        Toast.makeText(this@MainActivity, "Link added to queue", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val etRssSource = findViewById<EditText>(R.id.etRssSource)
        val btnAddRss = findViewById<Button>(R.id.btnAddRss)
        val tvRssList = findViewById<TextView>(R.id.tvRssList)

        val sharedPrefs = getSharedPreferences("rss_prefs", Context.MODE_PRIVATE)
        
        fun updateRssText() {
            val sources = sharedPrefs.getStringSet("rss_sources", emptySet())
            tvRssList.text = sources?.joinToString("\n") ?: "No sources added."
        }
        updateRssText()

        btnAddRss.setOnClickListener {
            val source = etRssSource.text.toString()
            if (source.isNotEmpty()) {
                val sources = sharedPrefs.getStringSet("rss_sources", emptySet())?.toMutableSet() ?: mutableSetOf()
                sources.add(source)
                sharedPrefs.edit().putStringSet("rss_sources", sources).apply()
                etRssSource.setText("")
                updateRssText()
            }
        }

        val rvTasks = findViewById<RecyclerView>(R.id.rvTasks)
        taskAdapter = TaskAdapter()
        rvTasks.adapter = taskAdapter
        rvTasks.layoutManager = LinearLayoutManager(this)
    }

    private fun startWorkerService() {
        val intent = Intent(this, WorkerService::class.java)
        startForegroundService(intent)
    }

    private fun scheduleRssWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val rssRequest = PeriodicWorkRequestBuilder<RssCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "RssCheckWork",
            ExistingPeriodicWorkPolicy.KEEP,
            rssRequest
        )
    }

    private fun observeTasks() {
        lifecycleScope.launch {
            database.taskDao().getAllTasks().collect { tasks ->
                taskAdapter.submitList(tasks)
            }
        }
    }

    private fun log(message: String) {
        val tvLogs = findViewById<TextView>(R.id.tvLogs)
        runOnUiThread {
            tvLogs.append("${System.currentTimeMillis()}: $message\n")
        }
    }
}
