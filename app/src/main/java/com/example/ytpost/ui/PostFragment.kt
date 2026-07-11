package com.example.ytpost.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.ytpost.AppLogger
import com.example.ytpost.ProxyManager
import com.example.ytpost.TelegramSessionManager
import com.example.ytpost.data.AppDatabase
import com.example.ytpost.data.DownloadPreferenceProfile
import com.example.ytpost.data.Task
import com.example.ytpost.databinding.FragmentPostBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class PostFragment : Fragment() {
    private var _binding: FragmentPostBinding? = null
    private val binding get() = _binding!!
    private lateinit var database: AppDatabase
    private lateinit var sessionManager: TelegramSessionManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = AppDatabase.getDatabase(requireContext())
        sessionManager = TelegramSessionManager.getInstance(requireContext())

        updateStatusCards()

        val sharedPrefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        binding.etDestination.setText(sharedPrefs.getString("default_destination", ""))

        observeActiveTask()

        binding.btnSendLink.setOnClickListener {
            val link = binding.etManualLink.text.toString().trim()
            val destination = binding.etDestination.text.toString().trim()

            if (link.isNotEmpty() && destination.isNotEmpty()) {
                sharedPrefs.edit().putString("default_destination", destination).apply()
                showPreview(link, destination)
            } else {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnShowLogs.setOnClickListener {
            LogsDialogFragment.newInstance().show(parentFragmentManager, "logs")
        }

        binding.btnShowFormats.setOnClickListener {
            val link = binding.etManualLink.text.toString().trim()
            if (link.isEmpty()) {
                Toast.makeText(context, "Enter link first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            fetchAndShowFormats(link)
        }
    }

    private fun fetchAndShowFormats(link: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val py = Python.getInstance()
            val downloader = py.getModule("downloader")
            
            withContext(Dispatchers.Main) {
                binding.btnShowFormats.isEnabled = false
                binding.btnShowFormats.text = "Loading Formats..."
            }

            try {
                val sharedPrefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val useManualProxy = sharedPrefs.getBoolean("use_manual_proxy", false)
                val currentProxy = if (useManualProxy) ProxyManager.detectProxy() else null
                val cookiePath = sharedPrefs.getString("cookie_file_path", null)
                
                val json = withTimeoutOrNull(30000) {
                    downloader.callAttr("get_video_formats", link, cookiePath, currentProxy).toString()
                }

                withContext(Dispatchers.Main) {
                    binding.btnShowFormats.isEnabled = true
                    binding.btnShowFormats.text = "Show Available Formats"
                    
                    if (json == null) {
                        Toast.makeText(context, "Timeout fetching formats", Toast.LENGTH_SHORT).show()
                    } else if (json.contains("error")) {
                        Toast.makeText(context, json, Toast.LENGTH_LONG).show()
                    } else {
                        FormatsDialogFragment.newInstance(json).show(parentFragmentManager, "formats")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.btnShowFormats.isEnabled = true
                    binding.btnShowFormats.text = "Show Available Formats"
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showPreview(link: String, destination: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(requireContext()))
            }
            val py = Python.getInstance()
            val downloader = py.getModule("downloader")
            
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.btnSendLink.isEnabled = false
                binding.btnSendLink.text = "Fetching Preview..."
            }

            try {
                // Detect proxy for preview
                val sharedPrefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val useManualProxy = sharedPrefs.getBoolean("use_manual_proxy", false)
                val currentProxy = if (useManualProxy) ProxyManager.detectProxy() else null

                val cookiePath = sharedPrefs.getString("cookie_file_path", null)
                
                val previewJson = withTimeoutOrNull(35000) {
                    downloader.callAttr("preview_media", link, cookiePath, currentProxy).toString()
                }
                
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    binding.btnSendLink.isEnabled = true
                    binding.btnSendLink.text = "SEND TO TELEGRAM"
                    
                    if (previewJson == null) {
                        Toast.makeText(context, "Timeout: Network is too slow", Toast.LENGTH_LONG).show()
                        return@withContext
                    }

                    val dialog = PreviewDialogFragment.newInstance(previewJson, link)
                    dialog.setOnConfirmListener { quality, onlyFirst, filter, customCaption, audioOnly, writeSubs, _, customArgs, saveDefault ->
                        saveTask(link, destination, quality, onlyFirst, filter, customCaption, audioOnly, writeSubs, customArgs, saveDefault)
                    }
                    dialog.show(parentFragmentManager, "preview")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    binding.btnSendLink.isEnabled = true
                    binding.btnSendLink.text = "SEND TO TELEGRAM"
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveTask(link: String, destination: String, quality: String, onlyFirst: Boolean, filter: String?, customCaption: String?, audioOnly: Boolean, writeSubs: Boolean, customArgs: String?, saveDefault: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            if (saveDefault) {
                val pref = DownloadPreferenceProfile(
                    sourceType = "manual",
                    sourceIdentifier = null,
                    defaultQuality = quality,
                    includeCarousel = !onlyFirst,
                    allowedMediaTypes = filter ?: "video,photo,audio",
                    useDefaultCaption = customCaption != null
                )
                database.downloadPreferenceDao().insert(pref)
            }

            database.taskDao().insert(Task(
                sourceUrl = link,
                destination = destination,
                status = "queued",
                quality = quality,
                onlyFirstItem = onlyFirst,
                mediaFilter = filter,
                useDefaultCaption = customCaption != null,
                customCaption = customCaption,
                audioOnly = audioOnly,
                writeSubs = writeSubs,
                customArgs = customArgs
            ))
            
            AppLogger.log("Manual post added with custom settings: $link")
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.etManualLink.setText("")
                Toast.makeText(context, "Added to queue with settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateStatusCards() {
        if (_binding == null) return
        if (sessionManager.getSessionString() != null) {
            binding.tvLoginStatus.text = "Connected"
            binding.tvLoginStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        } else {
            binding.tvLoginStatus.text = "Logged Out"
            binding.tvLoginStatus.setTextColor(android.graphics.Color.parseColor("#F44336"))
        }
        
        binding.tvWorkerStatus.text = "Active"
    }

    private fun observeActiveTask() {
        viewLifecycleOwner.lifecycleScope.launch {
            database.taskDao().getAllTasks().collect { tasks ->
                val activeTask = tasks.find { it.status == "downloading" || it.status == "uploading" }
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    if (activeTask != null) {
                        binding.cardLiveActivity.visibility = View.VISIBLE
                        val status = activeTask.status.lowercase()
                        val statusText = when {
                            status.contains("download") -> "📥 Downloading..."
                            status.contains("upload") -> "📤 Uploading..."
                            else -> "${activeTask.status.replaceFirstChar { it.uppercase() }}..."
                        }
                        binding.tvLiveStatus.text = statusText
                        binding.liveProgress.progress = activeTask.progress
                        binding.tvLivePercent.text = "${activeTask.progress}%"
                    } else {
                        binding.cardLiveActivity.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
