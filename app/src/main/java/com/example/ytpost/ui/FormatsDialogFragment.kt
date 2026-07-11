package com.example.ytpost.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.ytpost.databinding.DialogFormatsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.json.JSONArray

class FormatsDialogFragment : BottomSheetDialogFragment() {
    private var _binding: DialogFormatsBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(formatsJson: String): FormatsDialogFragment {
            val fragment = FormatsDialogFragment()
            val args = Bundle()
            args.putString("json", formatsJson)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogFormatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val json = arguments?.getString("json") ?: "[]"
        parseAndDisplay(json)
        
        binding.btnClose.setOnClickListener { dismiss() }
    }

    private fun parseAndDisplay(json: String) {
        try {
            val arr = JSONArray(json)
            val sb = StringBuilder()
            for (i in 0 until arr.length()) {
                val f = arr.getJSONObject(i)
                sb.append("ID: ${f.optString("format_id")}\n")
                sb.append("Ext: ${f.optString("ext")} | Res: ${f.optString("resolution")}\n")
                sb.append("FPS: ${f.optString("fps")} | Size: ${formatSize(f.optLong("filesize_approx"))}\n")
                sb.append("Type: ${if (f.optBoolean("is_progressive")) "Progressive" else "Single Stream"}\n")
                sb.append("----------------------------\n")
            }
            binding.tvFormats.text = if (sb.isEmpty()) "No formats found" else sb.toString()
        } catch (e: Exception) {
            binding.tvFormats.text = "Error parsing formats: ${e.message}"
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "Unknown"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1) "%.2f MB".format(mb) else "%.2f KB".format(kb)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
