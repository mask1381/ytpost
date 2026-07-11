package com.example.ytpost.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaquo.python.Python
import com.example.ytpost.AppLogger
import com.example.ytpost.ProxyManager
import com.example.ytpost.TelegramSessionManager
import com.example.ytpost.data.AppDatabase
import com.example.ytpost.data.TelegramChat
import com.example.ytpost.databinding.DialogChatSelectorBinding
import com.example.ytpost.databinding.ItemTelegramChatBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class ChatSelectorDialogFragment : BottomSheetDialogFragment() {
    private var _binding: DialogChatSelectorBinding? = null
    private val binding get() = _binding!!
    private lateinit var database: AppDatabase
    private var onChatSelected: ((Long) -> Unit)? = null

    private val adapter = ChatAdapter { chat ->
        onChatSelected?.invoke(chat.chatId)
        dismiss()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogChatSelectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = AppDatabase.getDatabase(requireContext())

        binding.rvChats.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChats.adapter = adapter

        observeCache()
        
        binding.btnRefresh.setOnClickListener { fetchFromTelegram() }
        binding.btnClose.setOnClickListener { dismiss() }

        // Initial fetch if cache empty
        viewLifecycleOwner.lifecycleScope.launch {
            if (database.telegramChatDao().getCount() == 0) {
                fetchFromTelegram()
            }
        }
    }

    private fun observeCache() {
        viewLifecycleOwner.lifecycleScope.launch {
            database.telegramChatDao().getAllChats().collect { chats ->
                adapter.submitList(chats)
            }
        }
    }

    private fun fetchFromTelegram() {
        val sessionManager = TelegramSessionManager.getInstance(requireContext())
        val apiId = sessionManager.getApiId()
        val apiHash = sessionManager.getApiHash()
        val sessionStr = sessionManager.getSessionString()

        if (apiId == null || apiHash == null || sessionStr == null) {
            Toast.makeText(context, "Please login to Telegram first", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnRefresh.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val py = Python.getInstance()
                val module = py.getModule("telegram_auth")
                val proxy = ProxyManager.detectProxy()
                
                val result = module.callAttr("fetch_postable_chats", apiId, apiHash, sessionStr, proxy).toString()

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRefresh.isEnabled = true

                    if (result.startsWith("ERROR:")) {
                        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                    } else if (result.startsWith("FLOOD_WAIT:")) {
                        val seconds = result.substringAfter(":").toInt()
                        Toast.makeText(context, "Flood Wait: Please wait $seconds seconds", Toast.LENGTH_LONG).show()
                    } else {
                        val jsonArr = JSONArray(result)
                        val chats = mutableListOf<TelegramChat>()
                        for (i in 0 until jsonArr.length()) {
                            val obj = jsonArr.getJSONObject(i)
                            chats.add(TelegramChat(
                                chatId = obj.getLong("id"),
                                title = obj.getString("title"),
                                type = obj.getString("type"),
                                username = obj.optString("username", null),
                                participantsCount = if (obj.has("participants_count")) obj.getInt("participants_count") else null
                            ))
                        }
                        
                        database.telegramChatDao().clearAll()
                        database.telegramChatDao().insertAll(chats)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRefresh.isEnabled = true
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun setOnChatSelectedListener(listener: (Long) -> Unit) {
        onChatSelected = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class ChatAdapter(private val onClick: (TelegramChat) -> Unit) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {
        private var items = listOf<TelegramChat>()
        fun submitList(newItems: List<TelegramChat>) { items = newItems; notifyDataSetChanged() }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            ItemTelegramChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
        override fun getItemCount() = items.size

        inner class ViewHolder(private val binding: ItemTelegramChatBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: TelegramChat) {
                binding.tvChatTitle.text = item.title
                val details = StringBuilder()
                details.append(item.type.replaceFirstChar { it.uppercase() })
                if (item.username != null) details.append(" | @${item.username}")
                if (item.participantsCount != null) details.append(" | ${item.participantsCount} members")
                binding.tvChatDetails.text = details.toString()
                
                val icon = when(item.type) {
                    "channel" -> android.graphics.drawable.Icon.createWithResource(itemView.context, android.R.drawable.ic_menu_myplaces) // Placeholder
                    else -> android.graphics.drawable.Icon.createWithResource(itemView.context, android.R.drawable.ic_menu_agenda) // Placeholder
                }
                // Placeholder for dynamic icon logic if needed
                
                itemView.setOnClickListener { onClick(item) }
            }
        }
    }
}
