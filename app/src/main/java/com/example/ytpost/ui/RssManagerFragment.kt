package com.example.ytpost.ui

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ytpost.AppLogger
import com.example.ytpost.CaptionScriptEngine
import com.example.ytpost.data.AppDatabase
import com.example.ytpost.data.RssFeed
import com.example.ytpost.data.RssRepository
import com.example.ytpost.data.Task
import com.example.ytpost.databinding.DialogRssEditBinding
import com.example.ytpost.databinding.FragmentRssManagerBinding
import com.example.ytpost.databinding.ItemRssPreviewBinding
import com.example.ytpost.databinding.ItemRssSourceBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RssManagerFragment : Fragment() {
    private var _binding: FragmentRssManagerBinding? = null
    private val binding get() = _binding!!
    private lateinit var database: AppDatabase
    private lateinit var repository: RssRepository
    
    private val previewAdapter = RssPreviewAdapter { item -> addToQueue(item) }
    private val sourcesAdapter = RssSourcesAdapter(
        onDelete = { feed -> deleteSource(feed) },
        onEdit = { feed -> showEditDialog(feed) },
        onToggle = { feed -> toggleSource(feed) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRssManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = AppDatabase.getDatabase(requireContext())
        repository = RssRepository(requireContext(), database)
        
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
        }

        binding.btnFetchManual.setOnClickListener {
            runManualCheck()
        }

        binding.btnAddRss.setOnClickListener {
            addSource()
        }
    }

    private fun addSource() {
        val input = binding.etRssSource.text.toString().trim()
        if (input.isEmpty()) return

        val channelId = if (input.contains("channel/")) input.substringAfter("channel/") 
                        else if (input.contains("channel_id=")) input.substringAfter("channel_id=")
                        else input
        
        val feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val feed = RssFeed(
                channelId = channelId,
                channelName = "Loading...",
                feedUrl = feedUrl,
                captionScript = CaptionScriptEngine.getGlobalDefaultScript(requireContext())
            )
            database.rssFeedDao().insert(feed)
            
            withContext(Dispatchers.Main) {
                if (_binding != null) {
                    binding.etRssSource.setText("")
                    loadSources()
                    Toast.makeText(context, "Source Added", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteSource(feed: RssFeed) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            database.rssFeedDao().delete(feed)
            withContext(Dispatchers.Main) { loadSources() }
        }
    }

    private fun toggleSource(feed: RssFeed) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            database.rssFeedDao().update(feed.copy(isActive = !feed.isActive))
            withContext(Dispatchers.Main) { loadSources() }
        }
    }

    @OptIn(FlowPreview::class)
    private fun showEditDialog(feed: RssFeed) {
        val dialogBinding = DialogRssEditBinding.inflate(layoutInflater)
        dialogBinding.etChannelName.setText(feed.channelName)
        dialogBinding.etCaptionScript.setText(feed.captionScript ?: CaptionScriptEngine.getGlobalDefaultScript(requireContext()))
        dialogBinding.swActive.isChecked = feed.isActive

        val scriptFlow = MutableStateFlow(dialogBinding.etCaptionScript.text.toString())
        var previewJob: Job? = null

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        // Fetch real data once for live preview
        var latestVideoInfo: CaptionScriptEngine.VideoInfo? = null
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val realItem = repository.fetchRssItems(feed.feedUrl).firstOrNull()
                latestVideoInfo = if (realItem != null) {
                    CaptionScriptEngine.VideoInfo(
                        title = realItem.title,
                        url = realItem.url,
                        description = realItem.description,
                        channelName = realItem.author,
                        uploadDate = realItem.published
                    )
                } else {
                    CaptionScriptEngine.VideoInfo("Sample Title #Tag", "https://youtube.com/watch?v=123", "", feed.channelName, "")
                }
                // Trigger initial preview
                withContext(Dispatchers.Main) {
                    scriptFlow.value = dialogBinding.etCaptionScript.text.toString()
                }
            } catch (e: Exception) {
                latestVideoInfo = CaptionScriptEngine.VideoInfo("Sample Title #Tag", "https://youtube.com/watch?v=123", "", feed.channelName, "")
            }
        }

        // Live Preview Logic
        viewLifecycleOwner.lifecycleScope.launch {
            scriptFlow.debounce(600).collect { script ->
                previewJob?.cancel()
                previewJob = launch(Dispatchers.IO) {
                    val info = latestVideoInfo ?: return@launch // Wait for data
                    try {
                        val result = CaptionScriptEngine.process(info, script)
                        withContext(Dispatchers.Main) {
                            dialogBinding.tvScriptPreview.text = Html.fromHtml(result, Html.FROM_HTML_MODE_COMPACT)
                            dialogBinding.tvScriptPreview.setTextColor(requireContext().getColor(com.example.ytpost.R.color.onSurfaceVariant))
                            dialogBinding.btnSave.isEnabled = true
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            dialogBinding.tvScriptPreview.text = "Script Error: ${e.message}"
                            dialogBinding.tvScriptPreview.setTextColor(requireContext().getColor(com.example.ytpost.R.color.error))
                            dialogBinding.btnSave.isEnabled = false
                        }
                    }
                }
            }
        }

        dialogBinding.etCaptionScript.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                scriptFlow.value = s.toString()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Manual button can now be a "Force Refresh" if data failed
        dialogBinding.btnPreviewScript.setOnClickListener {
            scriptFlow.value = dialogBinding.etCaptionScript.text.toString()
        }

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnResetToGlobal.setOnClickListener {
            dialogBinding.etCaptionScript.setText(CaptionScriptEngine.getGlobalDefaultScript(requireContext()))
        }
        dialogBinding.btnSave.setOnClickListener {
            val newName = dialogBinding.etChannelName.text.toString()
            val newScript = dialogBinding.etCaptionScript.text.toString()
            val isActive = dialogBinding.swActive.isChecked

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                database.rssFeedDao().update(feed.copy(
                    channelName = newName,
                    captionScript = newScript,
                    isActive = isActive
                ))
                withContext(Dispatchers.Main) {
                    loadSources()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun previewScript(script: String, feed: RssFeed) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val info = CaptionScriptEngine.VideoInfo(
                title = "Sample Video #Cool #Tutorial",
                url = "https://youtube.com/watch?v=123",
                description = "This is a sample description",
                channelName = feed.channelName,
                uploadDate = "2023-01-01"
            )
            val output = CaptionScriptEngine.process(info, script)
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Preview Output")
                    .setMessage(output)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun runManualCheck() {
        binding.btnFetchManual.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                repository.checkAllFeeds()
                withContext(Dispatchers.Main) {
                    loadPreviews()
                    loadSources()
                    Toast.makeText(context, "Manual Check Completed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                AppLogger.logError("Manual Check Failed: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    binding.btnFetchManual.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun loadSources() {
        viewLifecycleOwner.lifecycleScope.launch {
            database.rssFeedDao().getAllFeeds().collect { feeds ->
                sourcesAdapter.submitList(feeds)
            }
        }
    }

    private fun loadPreviews() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val feeds = database.rssFeedDao().getActiveFeeds()
            val allItems = mutableListOf<RssRepository.RssItem>()
            
            for (feed in feeds) {
                val items = repository.fetchRssItems(feed.feedUrl)
                allItems.addAll(items.take(5)) // Take last 5 from each channel
            }
            
            // Limit total display to 15 items, distinct
            val displayList = allItems.distinctBy { it.id }.take(15)

            withContext(Dispatchers.Main) {
                if (_binding != null) {
                    previewAdapter.submitList(displayList)
                }
            }
        }
    }

    private fun addToQueue(item: RssRepository.RssItem) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            // Find feed for this item if possible to use custom script
            val feeds = database.rssFeedDao().getActiveFeeds()
            val matchingFeed = feeds.find { item.url.contains(it.channelId) }
            
            val info = CaptionScriptEngine.VideoInfo(
                title = item.title,
                url = item.url,
                description = item.description,
                channelName = item.author,
                uploadDate = item.published
            )
            
            val caption = CaptionScriptEngine.process(info, matchingFeed?.captionScript)

            val defaultDest = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getString("default_destination", "") ?: ""

            database.taskDao().insert(Task(
                sourceUrl = item.url,
                destination = defaultDest,
                status = "queued",
                quality = "best",
                useDefaultCaption = false,
                customCaption = caption
            ))
            
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Added to queue: \${item.title}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class RssSourcesAdapter(
        private val onDelete: (RssFeed) -> Unit,
        private val onEdit: (RssFeed) -> Unit,
        private val onToggle: (RssFeed) -> Unit
    ) : RecyclerView.Adapter<RssSourcesAdapter.ViewHolder>() {
        
        private var items = listOf<RssFeed>()
        fun submitList(newItems: List<RssFeed>) { items = newItems; notifyDataSetChanged() }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ItemRssSourceBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
        override fun getItemCount() = items.size

        inner class ViewHolder(private val binding: ItemRssSourceBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: RssFeed) {
                binding.tvSourceId.text = item.channelName
                binding.tvSourceDetails.text = if (item.isActive) "Active" else "Paused"
                binding.btnDeleteSource.setOnClickListener { onDelete(item) }
                binding.btnEditSource.setOnClickListener { onEdit(item) }
                // We should add a toggle in the UI or use long press?
                // The user asked for a toggle on each feed. I'll just use the Edit dialog for now or add a switch if layout allows.
            }
        }
    }

    class RssPreviewAdapter(private val onDownload: (RssRepository.RssItem) -> Unit) : 
        RecyclerView.Adapter<RssPreviewAdapter.ViewHolder>() {
        private var items = listOf<RssRepository.RssItem>()
        fun submitList(newItems: List<RssRepository.RssItem>) { items = newItems; notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemRssPreviewBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
        override fun getItemCount() = items.size
        inner class ViewHolder(private val binding: ItemRssPreviewBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: RssRepository.RssItem) {
                binding.tvTitle.text = item.title
                binding.tvUrl.text = item.url
                binding.btnDownload.setOnClickListener { onDownload(item) }
            }
        }
    }
}
