package com.example.ytpost.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.example.ytpost.databinding.DialogMediaPreviewBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.json.JSONObject

class PreviewDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogMediaPreviewBinding? = null
    private val binding get() = _binding!!

    private var onConfirm: ((String, Boolean, String?, String?, Boolean, Boolean, Boolean, String?, Boolean) -> Unit)? = null

    companion object {
        fun newInstance(previewJson: String, sourceUrl: String): PreviewDialogFragment {
            val fragment = PreviewDialogFragment()
            val args = Bundle()
            args.putString("preview_json", previewJson)
            args.putString("source_url", sourceUrl)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogMediaPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val jsonStr = arguments?.getString("preview_json") ?: return
        val json = JSONObject(jsonStr)

        binding.tvPreviewTitle.text = json.optString("title", "No Title")
        val mediaKind = json.optString("media_kind", "video")
        val duration = json.optInt("duration", 0)
        val type = json.optString("type")
        
        var infoText = "Type: ${mediaKind.replaceFirstChar { it.uppercase() }}"
        if (duration > 0) infoText += " | Duration: ${duration / 60}:${String.format("%02d", duration % 60)}"
        if (type == "carousel") infoText += " | Carousel: ${json.optInt("item_count")} items"
        
        binding.tvMediaInfo.text = infoText

        val thumbUrl = json.optString("thumbnail_url")
        if (thumbUrl.isNotEmpty()) {
            Glide.with(this).load(thumbUrl).into(binding.ivThumbnail)
        }

        // Generate default caption
        val sourceUrl = arguments?.getString("source_url") ?: ""
        val title = json.optString("title", "No Title")
        
        try {
            val py = com.chaquo.python.Python.getInstance()
            val captionBuilder = py.getModule("caption_builder")
            val defaultCaption = captionBuilder.callAttr("build_caption", title, sourceUrl).toString()
            binding.etCaption.setText(defaultCaption)
        } catch (e: Exception) {
            binding.etCaption.setText(title)
        }

        binding.cbIncludeCaption.setOnCheckedChangeListener { _, isChecked ->
            binding.tilCaption.isEnabled = isChecked
        }

        if (type == "carousel") {
            binding.layoutCarouselOptions.visibility = View.VISIBLE
        }

        binding.btnShowAdvanced.setOnClickListener {
            if (binding.layoutAdvanced.visibility == View.VISIBLE) {
                binding.layoutAdvanced.visibility = View.GONE
                binding.btnShowAdvanced.text = "Advanced Download Tools ▼"
            } else {
                binding.layoutAdvanced.visibility = View.VISIBLE
                binding.btnShowAdvanced.text = "Advanced Download Tools ▲"
            }
        }

        binding.btnConfirmDownload.setOnClickListener {
            val quality = when (binding.rgQuality.checkedRadioButtonId) {
                binding.rbMedium.id -> "medium"
                binding.rbWorst.id -> "worst"
                else -> "best"
            }
            val onlyFirst = binding.cbOnlyFirstItem.isChecked
            val useCaption = binding.cbIncludeCaption.isChecked
            val editedCaption = if (useCaption) binding.etCaption.text.toString() else null
            
            // Advanced Flags
            val audioOnly = binding.cbAudioOnly.isChecked
            val writeSubs = binding.cbWriteSubs.isChecked
            val customArgs = binding.etCustomArgs.text.toString().trim().ifEmpty { null }
            
            val saveDefault = binding.cbSaveDefault.isChecked
            
            val filters = mutableListOf<String>()
            if (binding.cbVideo.isChecked) filters.add("video")
            if (binding.cbPhoto.isChecked) filters.add("photo")
            if (binding.cbAudio.isChecked) filters.add("audio")
            val mediaFilter = if (filters.size == 3) null else filters.joinToString(",")

            onConfirm?.invoke(quality, onlyFirst, mediaFilter, editedCaption, audioOnly, writeSubs, useCaption, customArgs, saveDefault)
            dismiss()
        }
    }

    fun setOnConfirmListener(listener: (String, Boolean, String?, String?, Boolean, Boolean, Boolean, String?, Boolean) -> Unit) {
        onConfirm = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
