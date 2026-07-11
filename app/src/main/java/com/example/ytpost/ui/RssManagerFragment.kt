package com.example.ytpost.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaquo.python.Python
import com.example.ytpost.AppLogger
import com.example.ytpost.CaptionEngine
import com.example.ytpost.RssWorker
import com.example.ytpost.data.AppDatabase
import com.example.ytpost.data.DownloadPreferenceProfile
import com.example.ytpost.data.RssHistory
import com.example.ytpost.data.Task
import com.example.ytpost.databinding.FragmentRssManagerBinding
import com.example.ytpost.databinding.ItemRssPreviewBinding
import com.example.ytpost.databinding.ItemRssSourceBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

class RssManagerFragment : Fragment() {
    private var _binding: FragmentRssManagerBinding? = null
    private val binding get() = _binding!!
    private lateinit var database: AppDatabase
    
    private val previewAdapter = RssPreviewAdapter { item -> addToQueue(item) }
    private val sourcesAdapter = RssSourcesAdapter(
        onDelete = { source -> deleteSource(source) },
        onEdit = { source -> showEditDialog(source) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRssManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = AppDatabase.getDatabase(requireContext())
        
        binding.rvRssSources.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRssSources.adapter = sourcesAdapter

        binding.rvRssPreview.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRssPreview.adapter = previewAdapter

        setupControls()
        loadSources()
        loadPreviews()
    }

    private fun setupControls() {
        val prefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        binding.swAutoRss.isChecked = prefs.getBoolean("rss_auto_enabled", true)
        
        binding.swAutoRss.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("rss_auto_enabled", isChecked).apply()
            AppLogger.log("RSS Auto: $isChecked")
        }

        binding.btnFetchManual.setOnClickListener {
            refreshFeeds()
        }

        binding.btnAddRss.setOnClickListener {
            addSource()
        }
    }

    private fun addSource() {
        val source = binding.etRssSource.text.toString().trim()
        if (source.isEmpty()) return

        val includeCarousel = binding.cbRssCarousel.isChecked
        val includeCaption = binding.cbRssCaption.isChecked
        val template = binding.etRssTemplate.text.toString().trim()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val profile = DownloadPreferenceProfile(
                sourceType = "rss",
                sourceIdentifier = source,
                defaultQuality = "best",
                includeCarousel = includeCarousel,
                allowedMediaTypes = "video,photo,audio",
                useDefaultCaption = includeCaption,
                captionTemplate = if (template.isNotEmpty()) template else null,
                parseMode = "HTML"
            )
            database.downloadPreferenceDao().insert(profile)

            // Update legacy preferences for compatibility
            val rssPrefs = requireActivity().getSharedPreferences("rss_prefs", Context.MODE_PRIVATE)
            val sources = rssPrefs.getStringSet("rss_sources", emptySet())?.toMutableSet() ?: mutableSetOf()
            sources.add(source)
            rssPrefs.edit().putStringSet("rss_sources", sources).apply()

            withContext(Dispatchers.Main) {
                binding.etRssSource.setText("")
                loadSources()
                loadPreviews()
                Toast.makeText(context, "Source Added", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteSource(source: DownloadPreferenceProfile) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            database.downloadPreferenceDao().delete(source)
            
            val rssPrefs = requireActivity().getSharedPreferences("rss_prefs", Context.MODE_PRIVATE)
            val sources = rssPrefs.getStringSet("rss_sources", emptySet())?.toMutableSet() ?: mutableSetOf()
            sources.remove(source.sourceIdentifier)
            rssPrefs.edit().putStringSet("rss_sources", sources).apply()

            withContext(Dispatchers.Main) {
                loadSources()
                loadPreviews()
                Toast.makeText(context, "Source Removed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditDialog(source: DownloadPreferenceProfile) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(com.example.ytpost.R.layout.dialog_rss_edit, null)
        val etTemplate = dialogView.findViewById<android.widget.EditText>(com.example.ytpost.R.id.etTemplate)
        val etJsonRules = dialogView.findViewById<android.widget.EditText>(com.example.ytpost.R.id.etJsonRules)
        val tvId = dialogView.findViewById<android.widget.TextView>(com.example.ytpost.R.id.tvSourceId)

        tvId.text = "Editing: ${source.sourceIdentifier}"
        etTemplate.setText(source.captionTemplate ?: "{title}\n{url}")
        etJsonRules.setText(source.captionRulesJson ?: "[]")

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(com.example.ytpost.R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(com.example.ytpost.R.id.btnSave).setOnClickListener {
            val newTemplate = etTemplate.text.toString().trim()
            val newJson = etJsonRules.text.toString().trim()

            // Basic JSON validation
            try {
                org.json.JSONArray(newJson)
            } catch (e: Exception) {
                Toast.makeText(context, "Invalid JSON format", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val updated = source.copy(
                    captionTemplate = newTemplate,
                    captionRulesJson = newJson
                )
                database.downloadPreferenceDao().update(updated)
                withContext(Dispatchers.Main) {
                    loadSources()
                    dialog.dismiss()
                    Toast.makeText(context, "Settings Saved", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun loadSources() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val sources = database.downloadPreferenceDao().getAll().filter { it.sourceType == "rss" }
            withContext(Dispatchers.Main) {
                if (_binding != null) {
                    sourcesAdapter.submitList(sources)
                }
            }
        }
    }

    private fun refreshFeeds() {
        binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                loadPreviews()
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Sync Completed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    AppLogger.logError("Manual RSS Error: ${e.message}")
                }
            }
        }
    }

    private fun loadPreviews() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val rssSources = database.downloadPreferenceDao().getAll().filter { it.sourceType == "rss" }
            val allItems = mutableListOf<RssWorker.RssItem>()
            
            for (source in rssSources) {
                val channelId = source.sourceIdentifier ?: continue
                val rssUrl = if (channelId.startsWith("http")) channelId 
                             else "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
                try {
                    val items = fetchRssItems(rssUrl).take(5) 
                    allItems.addAll(items)
                } catch (e: Exception) {}
            }
            
            val displayList = allItems.distinctBy { it.id }.take(10)

            withContext(Dispatchers.Main) {
                if (_binding != null) {
                    previewAdapter.submitList(displayList)
                }
            }
        }
    }

    private fun fetchRssItems(url: String): List<RssWorker.RssItem> {
        val items = mutableListOf<RssWorker.RssItem>()
        try {
            val conn = URL(url).openConnection()
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(conn.getInputStream())
            val entries = doc.getElementsByTagName("entry")

            for (i in 0 until entries.length) {
                val entry = entries.item(i) as Element
                val id = entry.getElementsByTagName("yt:videoId").item(0)?.textContent ?: continue
                val title = entry.getElementsByTagName("title").item(0)?.textContent ?: "No Title"
                val link = "https://www.youtube.com/watch?v=$id"
                items.add(RssWorker.RssItem(id, title, link))
            }
        } catch (e: Exception) {}
        return items
    }

    private fun addToQueue(item: RssWorker.RssItem) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            // Find specific pref for this source
            val channelId = item.url.substringAfter("channel_id=", item.url.substringAfter("v=", ""))
            val pref = database.downloadPreferenceDao().getPreference("rss", channelId)
            
            val caption = CaptionEngine.process(item.title, item.url, pref)

            database.taskDao().insert(Task(
                sourceUrl = item.url,
                destination = "",
                status = "queued",
                quality = pref?.defaultQuality ?: "best",
                useDefaultCaption = true,
                customCaption = caption
            ))
            
            database.rssHistoryDao().insert(RssHistory(item.id, item.url))
            
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Added to queue: ${item.title}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- Adapters ---

    class RssSourcesAdapter(
        private val onDelete: (DownloadPreferenceProfile) -> Unit,
        private val onEdit: (DownloadPreferenceProfile) -> Unit
    ) : RecyclerView.Adapter<RssSourcesAdapter.ViewHolder>() {
        
        private var items = listOf<DownloadPreferenceProfile>()

        fun submitList(newItems: List<DownloadPreferenceProfile>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemRssSourceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
        override fun getItemCount() = items.size

        inner class ViewHolder(private val binding: ItemRssSourceBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: DownloadPreferenceProfile) {
                binding.tvSourceId.text = item.sourceIdentifier
                binding.tvSourceDetails.text = "Quality: ${item.defaultQuality} | Carousel: ${item.includeCarousel}"
                binding.btnDeleteSource.setOnClickListener { onDelete(item) }
                binding.btnEditSource.setOnClickListener { onEdit(item) }
            }
        }
    }

    class RssPreviewAdapter(private val onDownload: (RssWorker.RssItem) -> Unit) : 
        RecyclerView.Adapter<RssPreviewAdapter.ViewHolder>() {
        
        private var items = listOf<RssWorker.RssItem>()

        fun submitList(newItems: List<RssWorker.RssItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemRssPreviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
        override fun getItemCount() = items.size

        inner class ViewHolder(private val binding: ItemRssPreviewBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: RssWorker.RssItem) {
                binding.tvTitle.text = item.title
                binding.tvUrl.text = item.url
                binding.btnDownload.setOnClickListener { onDownload(item) }
            }
        }
    }
}
