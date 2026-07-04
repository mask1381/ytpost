package com.example.ytpost

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.work.*
import com.example.ytpost.databinding.ActivityMainBinding
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // تنظیم نوار ابزار بالا
        setSupportActionBar(binding.toolbar)

        // راه‌اندازی سیستم ناوبری (Navigation)
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)

        // شروع سرویس‌های پس‌زمینه
        startWorkerService()
        scheduleRssWorker()
        
        AppLogger.log("Application UI ready")
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
}
