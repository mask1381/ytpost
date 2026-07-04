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
import com.example.ytpost.TelegramSessionManager
import com.example.ytpost.data.AppDatabase
import com.example.ytpost.data.DownloadPreferenceProfile
import com.example.ytpost.data.Task
import com.example.ytpost.databinding.FragmentPostBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        sessionManager = TelegramSessionManager(requireContext())

        updateStatusCards()

        val sharedPrefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        binding.etDestination.setText(sharedPrefs.getString("default_destination", ""))

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
    }

    private fun showPreview(link: String, destination: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(requireContext()))
            }
            val py = Python.getInstance()
            val downloader = py.getModule("downloader")
            
            withContext(Dispatchers.Main) {
                binding.btnSendLink.isEnabled = false
                binding.btnSendLink.text = "Fetching Preview..."
            }

            try {
                val previewJson = downloader.callAttr("preview_media", link).toString()
                
                withContext(Dispatchers.Main) {
                    binding.btnSendLink.isEnabled = true
                    binding.btnSendLink.text = "SEND TO TELEGRAM"
                    
                    val dialog = PreviewDialogFragment.newInstance(previewJson)
                    dialog.setOnConfirmListener { quality, onlyFirst, filter, saveDefault ->
                        saveTask(link, destination, quality, onlyFirst, filter, saveDefault)
                    }
                    dialog.show(parentFragmentManager, "preview")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.btnSendLink.isEnabled = true
                    binding.btnSendLink.text = "SEND TO TELEGRAM"
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveTask(link: String, destination: String, quality: String, onlyFirst: Boolean, filter: String?, saveDefault: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            if (saveDefault) {
                val pref = DownloadPreferenceProfile(
                    sourceType = "manual",
                    sourceIdentifier = null,
                    defaultQuality = quality,
                    includeCarousel = !onlyFirst,
                    allowedMediaTypes = filter ?: "video,photo,audio"
                )
                database.downloadPreferenceDao().insert(pref)
            }

            database.taskDao().insert(Task(
                sourceUrl = link,
                destination = destination,
                status = "queued",
                quality = quality,
                onlyFirstItem = onlyFirst,
                mediaFilter = filter
            ))
            
            AppLogger.log("Manual post added with custom settings: $link")
            withContext(Dispatchers.Main) {
                binding.etManualLink.setText("")
                Toast.makeText(context, "Added to queue with settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateStatusCards() {
        if (sessionManager.getSessionString() != null) {
            binding.tvLoginStatus.text = "Connected"
            binding.tvLoginStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        } else {
            binding.tvLoginStatus.text = "Logged Out"
            binding.tvLoginStatus.setTextColor(android.graphics.Color.parseColor("#F44336"))
        }
        
        binding.tvWorkerStatus.text = "Active"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
