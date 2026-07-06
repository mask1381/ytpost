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
                val currentProxy = ProxyManager.detectProxy()
                
                val previewJson = withTimeoutOrNull(35000) {
                    downloader.callAttr("preview_media", link, null, currentProxy).toString()
                }
                
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    binding.btnSendLink.isEnabled = true
                    binding.btnSendLink.text = "SEND TO TELEGRAM"
                    
                    if (previewJson == null) {
                        Toast.makeText(context, "Timeout: Network is too slow", Toast.LENGTH_LONG).show()
                        return@withContext
                    }

                    val dialog = PreviewDialogFragment.newInstance(previewJson)
                    dialog.setOnConfirmListener { quality, onlyFirst, filter, saveDefault ->
                        saveTask(link, destination, quality, onlyFirst, filter, saveDefault)
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

    private fun saveTask(link: String, destination: String, quality: String, onlyFirst: Boolean, filter: String?, saveDefault: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
