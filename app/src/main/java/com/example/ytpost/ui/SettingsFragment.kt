package com.example.ytpost.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.chaquo.python.Python
import com.example.ytpost.TelegramSessionManager
import com.example.ytpost.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var sessionManager: TelegramSessionManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = TelegramSessionManager(requireContext())

        setupTelegramConfig()
        setupRssConfig()
    }

    private fun setupTelegramConfig() {
        binding.etApiId.setText(sessionManager.getApiId())
        binding.etApiHash.setText(sessionManager.getApiHash())
        binding.etPhone.setText(sessionManager.getPhoneNumber())

        if (sessionManager.getSessionString() != null) {
            binding.tvLoginStatus.text = "Status: Logged In ✅"
        }

        binding.btnSaveConfig.setOnClickListener {
            sessionManager.saveTelegramCredentials(
                binding.etApiId.text.toString(),
                binding.etApiHash.text.toString(),
                binding.etPhone.text.toString()
            )
            Toast.makeText(context, "Credentials Saved", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogin.setOnClickListener {
            val apiId = binding.etApiId.text.toString().trim()
            val apiHash = binding.etApiHash.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()

            if (apiId.isEmpty() || apiHash.isEmpty() || phone.isEmpty()) {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.tvLoginStatus.text = "Status: Connecting..."

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val py = Python.getInstance()
                    val module = py.getModule("telegram_auth")
                    val result = module.callAttr("request_code", apiId, apiHash, phone).toString()

                    withContext(Dispatchers.Main) {
                        if (result == "OK") {
                            binding.tvLoginStatus.text = "Status: Code Sent"
                            showCodeDialog()
                        } else {
                            binding.tvLoginStatus.text = "Status: Error"
                            Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.tvLoginStatus.text = "Status: System Error"
                        Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun setupRssConfig() {
        val rssPrefs = requireActivity().getSharedPreferences("rss_prefs", Context.MODE_PRIVATE)
        
        fun updateRssText() {
            val sources = rssPrefs.getStringSet("rss_sources", emptySet())
            binding.tvRssList.text = sources?.joinToString("\n") ?: "No sources."
        }
        updateRssText()

        binding.btnAddRss.setOnClickListener {
            val source = binding.etRssSource.text.toString().trim()
            if (source.isNotEmpty()) {
                val sources = rssPrefs.getStringSet("rss_sources", emptySet())?.toMutableSet() ?: mutableSetOf()
                sources.add(source)
                rssPrefs.edit().putStringSet("rss_sources", sources).apply()
                binding.etRssSource.setText("")
                updateRssText()
                Toast.makeText(context, "Source Added", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCodeDialog() {
        val input = EditText(requireContext())
        input.hint = "5-digit code"
        AlertDialog.Builder(requireContext())
            .setTitle("Verify Number")
            .setView(input)
            .setPositiveButton("Verify") { _, _ ->
                val code = input.text.toString().trim()
                submitLoginCode(code)
            }
            .setNegativeButton("Cancel", null)
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
                        result == "NEED_PASSWORD" -> showPasswordDialog()
                        result.startsWith("ERROR") -> Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                        else -> {
                            sessionManager.saveSessionString(result)
                            binding.tvLoginStatus.text = "Status: Logged In ✅"
                            Toast.makeText(context, "Success!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, e.message, Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun showPasswordDialog() {
        val input = EditText(requireContext())
        input.hint = "2FA Password"
        AlertDialog.Builder(requireContext())
            .setTitle("Two-Step Verification")
            .setView(input)
            .setPositiveButton("Submit") { _, _ ->
                val password = input.text.toString().trim()
                submitLoginPassword(password)
            }
            .show()
    }

    private fun submitLoginPassword(password: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val py = Python.getInstance()
                val module = py.getModule("telegram_auth")
                val result = module.callAttr("submit_password", password).toString()

                withContext(Dispatchers.Main) {
                    if (result.startsWith("ERROR")) {
                        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                    } else {
                        sessionManager.saveSessionString(result)
                        binding.tvLoginStatus.text = "Status: Logged In ✅"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, e.message, Toast.LENGTH_LONG).show() }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
