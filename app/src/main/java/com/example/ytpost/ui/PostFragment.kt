package com.example.ytpost.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.ytpost.AppLogger
import com.example.ytpost.TelegramSessionManager
import com.example.ytpost.data.AppDatabase
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

                lifecycleScope.launch(Dispatchers.IO) {
                    database.taskDao().insert(Task(sourceUrl = link, destination = destination, status = "queued"))
                    AppLogger.log("Manual post added to queue: $link")
                    withContext(Dispatchers.Main) {
                        binding.etManualLink.setText("")
                        Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
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
        
        // Default worker status
        binding.tvWorkerStatus.text = "Active"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
