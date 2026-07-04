package com.example.ytpost.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.ytpost.AppLogger
import com.example.ytpost.databinding.FragmentLogsBinding
import kotlinx.coroutines.launch

class LogsFragment : Fragment() {
    private var _binding: FragmentLogsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewLifecycleOwner.lifecycleScope.launch {
            AppLogger.logs.collect { logs ->
                binding.tvLogs.text = logs
            }
        }

        binding.btnClearLogs.setOnClickListener {
            // Since AppLogger is an object with StateFlow, we need a way to clear it.
            // I will add a clear function to AppLogger.
            AppLogger.clear()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
