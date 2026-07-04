package com.example.ytpost.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ytpost.AppLogger
import com.example.ytpost.TaskAdapter
import com.example.ytpost.data.AppDatabase
import com.example.ytpost.databinding.FragmentTasksBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TasksFragment : Fragment() {
    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!
    private lateinit var database: AppDatabase
    private lateinit var taskAdapter: TaskAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = AppDatabase.getDatabase(requireContext())
        
        taskAdapter = TaskAdapter { task ->
            // Delete task from database
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                database.taskDao().delete(task.id)
                AppLogger.log("Task deleted: ${task.sourceUrl}")
            }
        }
        
        binding.rvTasks.adapter = taskAdapter
        binding.rvTasks.layoutManager = LinearLayoutManager(context)

        viewLifecycleOwner.lifecycleScope.launch {
            database.taskDao().getAllTasks().collect { tasks ->
                if (_binding != null) {
                    taskAdapter.submitList(tasks)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
