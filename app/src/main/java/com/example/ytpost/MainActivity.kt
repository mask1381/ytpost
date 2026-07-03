package com.example.ytpost

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.*
import com.chaquo.python.Python
import com.example.ytpost.data.AppDatabase
import com.example.ytpost.data.Task
import com.example.ytpost.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private lateinit var sessionManager: TelegramSessionManager
    private lateinit var taskAdapter: TaskAdapter
    
    // 1. Declare the binding variable
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 2. Initialize binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)
        sessionManager = TelegramSessionManager(this)

        setupUI()
        startWorkerService()
        scheduleRssWorker()
        observeTasks()
    }

    private fun setupUI() {
        // 3. Access views directly via binding.ID (No more findViewById needed)
        binding.etApiId.setText(sessionManager.getApiId())
        binding.etApiHash.setText(sessionManager.getApiHash())
        binding.etPhone.setText(sessionManager.getPhoneNumber())

        binding.btnSaveConfig.setOnClickListener {
            sessionManager.saveTelegramCredentials(
                binding.etApiId.text.toString(),
                binding.etApiHash.text.toString(),
                binding.etPhone.text.toString()
            )
            Toast.makeText(this, "Config Saved", Toast.LENGTH_SHORT).show()
        }

        if (sessionManager.getSessionString() != null) {
            binding.tvLoginStatus.text = "وضعیت: وارد شده ✅"
        }

        binding.btnLogin.setOnClickListener {
            val apiId = binding.etApiId.text.toString().trim()
            val apiHash = binding.etApiHash.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()

            if (apiId.isEmpty() || apiHash.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "لطفاً API ID، API Hash و شماره رو پر کن", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sessionManager.saveTelegramCredentials(apiId, apiHash, phone)
            binding.tvLoginStatus.text = "وضعیت: در حال ارسال کد..."

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val py = Python.getInstance()
                    val module = py.getModule("telegram_auth")
                    val result = module.callAttr("request_code", apiId, apiHash, phone).toString()

                    withContext(Dispatchers.Main) {
                        if (result == "OK") {
                            binding.tvLoginStatus.text = "وضعیت: کد ارسال شد، وارد کن"
                            showCodeDialog()
                        } else {
                            binding.tvLoginStatus.text = "وضعیت: خطا"
                            Toast.makeText(this@MainActivity, result, Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.tvLoginStatus.text = "وضعیت: خطا در پایتون"
                        Toast.makeText(this@MainActivity, e.message ?: "Unknown error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        binding.btnSendLink.setOnClickListener {
            val link = binding.etManualLink.text.toString()
            if (link.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    database.taskDao().insert(Task(sourceUrl = link, status = "queued"))
                    withContext(Dispatchers.Main) {
                        binding.etManualLink.setText("")
                        Toast.makeText(this@MainActivity, "Link added to queue", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val sharedPrefs = getSharedPreferences("rss_prefs", Context.MODE_PRIVATE)
        
        fun updateRssText() {
            val sources = sharedPrefs.getStringSet("rss_sources", emptySet())
            binding.tvRssList.text = sources?.joinToString("\n") ?: "No sources added."
        }
        updateRssText()

        binding.btnAddRss.setOnClickListener {
            val source = binding.etRssSource.text.toString()
            if (source.isNotEmpty()) {
                val sources = sharedPrefs.getStringSet("rss_sources", emptySet())?.toMutableSet() ?: mutableSetOf()
                sources.add(source)
                sharedPrefs.edit().putStringSet("rss_sources", sources).apply()
                binding.etRssSource.setText("")
                updateRssText()
            }
        }

        taskAdapter = TaskAdapter()
        binding.rvTasks.adapter = taskAdapter
        binding.rvTasks.layoutManager = LinearLayoutManager(this)
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

    private fun showCodeDialog() {
        val input = EditText(this)
        input.hint = "کد تایید تلگرام"
        AlertDialog.Builder(this)
            .setTitle("کد تایید")
            .setView(input)
            .setPositiveButton("تایید") { _, _ ->
                val code = input.text.toString().trim()
                submitLoginCode(code)
            }
            .setNegativeButton("لغو", null)
            .setCancelable(false)
            .show()
    }

    private fun showPasswordDialog() {
        val input = EditText(this)
        input.hint = "رمز تایید دومرحله‌ای"
        AlertDialog.Builder(this)
            .setTitle("تایید دومرحله‌ای")
            .setView(input)
            .setPositiveButton("تایید") { _, _ ->
                val password = input.text.toString().trim()
                submitLoginPassword(password)
            }
            .setNegativeButton("لغو", null)
            .setCancelable(false)
            .show()
    }

    private fun submitLoginCode(code: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val py = Python.getInstance()
                val module = py.getModule("telegram_auth")
                val result = module.callAttr("submit_code", code).toString()

                withContext(Dispatchers.Main) {
                    when {
                        result == "NEED_PASSWORD" -> {
                            binding.tvLoginStatus.text = "وضعیت: نیاز به رمز دومرحله‌ای"
                            showPasswordDialog()
                        }
                        result.startsWith("ERROR") -> {
                            binding.tvLoginStatus.text = "وضعیت: خطا"
                            Toast.makeText(this@MainActivity, result, Toast.LENGTH_LONG).show()
                        }
                        else -> {
                            sessionManager.saveSessionString(result)
                            binding.tvLoginStatus.text = "وضعیت: وارد شده ✅"
                            Toast.makeText(this@MainActivity, "ورود موفق", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvLoginStatus.text = "وضعیت: خطا در پایتون"
                    Toast.makeText(this@MainActivity, e.message ?: "Unknown error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun submitLoginPassword(password: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val py = Python.getInstance()
                val module = py.getModule("telegram_auth")
                val result = module.callAttr("submit_password", password).toString()

                withContext(Dispatchers.Main) {
                    if (result.startsWith("ERROR")) {
                        binding.tvLoginStatus.text = "وضعیت: خطا"
                        Toast.makeText(this@MainActivity, result, Toast.LENGTH_LONG).show()
                    } else {
                        sessionManager.saveSessionString(result)
                        binding.tvLoginStatus.text = "وضعیت: وارد شده ✅"
                        Toast.makeText(this@MainActivity, "ورود موفق", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvLoginStatus.text = "وضعیت: خطا در پایتون"
                    Toast.makeText(this@MainActivity, e.message ?: "Unknown error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun log(message: String) {
        runOnUiThread {
            binding.tvLogs.append("${System.currentTimeMillis()}: $message\n")
        }
    }
}
