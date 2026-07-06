package com.example.ytpost

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ytpost.data.Task
import com.example.ytpost.databinding.ItemTaskBinding

class TaskAdapter(private val onDeleteClick: (Task) -> Unit) : ListAdapter<Task, TaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding, onDeleteClick)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TaskViewHolder(
        private val binding: ItemTaskBinding,
        private val onDeleteClick: (Task) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(task: Task) {
            binding.tvUrl.text = task.sourceUrl
            binding.tvDestination.text = "To: ${task.destination}"
            
            // Status Chip Logic
            binding.chipStatus.text = task.status.uppercase()
            val color = when (task.status.lowercase()) {
                "queued" -> "#FFB300"
                "downloading" -> "#03A9F4"
                "uploading" -> "#9C27B0"
                "done" -> "#4CAF50"
                "failed" -> "#F44336"
                else -> "#757575"
            }
            binding.chipStatus.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor(color))
            binding.chipStatus.setTextColor(Color.WHITE)
            
            // Progress Logic
            if (task.status.lowercase() == "downloading" || task.status.lowercase() == "uploading") {
                binding.progressIndicator.visibility = View.VISIBLE
                binding.tvProgressPercent.visibility = View.VISIBLE
                binding.progressIndicator.progress = task.progress
                binding.tvProgressPercent.text = "${task.progress}%"
            } else {
                binding.progressIndicator.visibility = View.GONE
                binding.tvProgressPercent.visibility = View.GONE
            }
            
            if (!task.errorMessage.isNullOrEmpty()) {
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = task.errorMessage
            } else {
                binding.tvError.visibility = View.GONE
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(task)
            }
        }
    }

    class TaskDiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean = oldItem == newItem
    }
}
