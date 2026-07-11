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
import com.example.ytpost.AppLogger
import com.example.ytpost.ProxyManager
import com.example.ytpost.TelegramSessionManager
import com.example.ytpost.data.AppDatabase
import com.example.ytpost.data.DownloadPreferenceProfile
import com.example.ytpost.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var sessionManager: TelegramSessionManager
    private lateinit var database: AppDatabase

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = TelegramSessionManager.getInstance(requireContext())
        database = AppDatabase.getDatabase(requireContext())

        setupTelegramConfig()
        setupProxyConfig()
        setupDebugInfo()
        
        updateCookieStatus()
    }

    private fun updateCookieStatus() {
        val sharedPrefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val path = sharedPrefs.getString("cookie_file_path", null)
        if (_binding != null) {
            binding.tvCookieStatus.text = if (path != null) "Cookies: ${java.io.File(path).name}" else "No cookie file set"
        }
    }

    private fun setupProxyConfig() {
        val sharedPrefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        binding.swManualProxy.isChecked = sharedPrefs.getBoolean("use_manual_proxy", false)

        binding.swManualProxy.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("use_manual_proxy", isChecked).apply()
        }

        binding.btnTestProxy.setOnClickListener {
            binding.tvProxyStatus.text = "Testing ports..."
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val results = ProxyManager.testAllProxies()
                val active = ProxyManager.detectProxy()
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    binding.tvProxyStatus.text = "Active Proxy: ${active ?: "None (Direct/VPN)"}\n\nFull Scan:\n$results"
                }
            }
        }
    }

    private fun setupDebugInfo() {
        binding.btnCheckVersion.setOnClickListener {
            binding.tvVersionInfo.text = "Checking versions & encoders..."
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val py = Python.getInstance()
                    val module = py.getModule("downloader")
                    
                    val ytdlpVersion = module.callAttr("get_ytdlp_version").toString()
                    
                    val nativeDir = requireContext().applicationInfo.nativeLibraryDir
                    val ffmpegPath = java.io.File(nativeDir, "libffmpeg.so").absolutePath
                    val encodersInfo = module.callAttr("check_ffmpeg_encoders", ffmpegPath).toString()
                    val ffprobeStatus = module.callAttr("run_ffprobe_test", nativeDir).toString()
                    
                    withContext(Dispatchers.Main) {
                        if (_binding == null) return@withContext
                        binding.tvVersionInfo.text = "yt-dlp: $ytdlpVersion\nFFmpeg check completed."
                        
                        val displayInfo = "--- Versions ---\n" +
                                "yt-dlp: $ytdlpVersion\n" +
                                "ffprobe: $ffprobeStatus\n\n" +
                                "--- FFmpeg Encoders ---\n" +
                                encodersInfo

                        AlertDialog.Builder(requireContext())
                            .setTitle("System Diagnostics")
                            .setMessage(displayInfo)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        if (_binding == null) return@withContext
                        binding.tvVersionInfo.text = "Error checking info"
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        binding.btnExportLogs.setOnClickListener {
            val logsText = AppLogger.logs.value
            val sendIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_TEXT, logsText)
                type = "text/plain"
            }
            val shareIntent = android.content.Intent.createChooser(sendIntent, "Export Logs")
            startActivity(shareIntent)
        }

        binding.btnUploadCookies.setOnClickListener {
            pickCookieFile()
        }
    }

    private val cookiePickerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            saveCookieFile(it)
        }
    }

    private fun pickCookieFile() {
        cookiePickerLauncher.launch("*/*")
    }

    private fun saveCookieFile(uri: android.net.Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val file = java.io.File(requireContext().filesDir, "cookies.txt")
                val outputStream = java.io.FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()

                val sharedPrefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                sharedPrefs.edit().putString("cookie_file_path", file.absolutePath).apply()

                withContext(Dispatchers.Main) {
                    updateCookieStatus()
                    Toast.makeText(context, "Cookies uploaded successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to upload cookies: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
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

            // Persistence fix: save credentials before logging in
            sessionManager.saveTelegramCredentials(apiId, apiHash, phone)

            binding.tvLoginStatus.text = "Status: Connecting..."

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val py = Python.getInstance()
                    val module = py.getModule("telegram_auth")
                    val proxy = ProxyManager.detectProxy()
                    
                    val result = withTimeoutOrNull(30000) {
                        module.callAttr("request_code", apiId, apiHash, phone, proxy).toString()
                    }

                    withContext(Dispatchers.Main) {
                        if (_binding == null) return@withContext
                        if (result == null) {
                            binding.tvLoginStatus.text = "Status: Timeout"
                            return@withContext
                        }
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
                        if (_binding == null) return@withContext
                        binding.tvLoginStatus.text = "Status: System Error"
                        Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                    }
                }
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
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val py = Python.getInstance()
                val module = py.getModule("telegram_auth")
                val result = withTimeoutOrNull(30000) {
                    module.callAttr("submit_code", code).toString()
                }

                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    if (result == null) {
                        Toast.makeText(context, "Timeout", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }
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
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show() 
                }
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
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val py = Python.getInstance()
                val module = py.getModule("telegram_auth")
                val result = withTimeoutOrNull(30000) {
                    module.callAttr("submit_password", password).toString()
                }

                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    if (result == null) {
                        Toast.makeText(context, "Timeout", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }
                    if (result.startsWith("ERROR")) {
                        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                    } else {
                        sessionManager.saveSessionString(result)
                        binding.tvLoginStatus.text = "Status: Logged In ✅"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { 
                    if (_binding == null) return@withContext
                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show() 
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
