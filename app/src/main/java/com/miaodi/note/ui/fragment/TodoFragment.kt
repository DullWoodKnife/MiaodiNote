package com.miaodi.note.ui.fragment

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.miaodi.note.MiaodiApplication
import com.miaodi.note.data.model.Todo
import com.miaodi.note.databinding.FragmentTodoBinding
import com.miaodi.note.ui.adapter.TodoAdapter
import kotlinx.coroutines.launch

class TodoFragment : Fragment() {

    private var _binding: FragmentTodoBinding? = null
    private val binding get() = _binding!!

    private lateinit var todoAdapter: TodoAdapter
    private val repository by lazy {
        (requireActivity().application as MiaodiApplication).repository
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTodoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        todoAdapter = TodoAdapter(
            onToggle = { todo ->
                lifecycleScope.launch {
                    repository.updateTodo(todo.copy(updatedAt = System.currentTimeMillis()))
                }
            },
            onDelete = { todo ->
                AlertDialog.Builder(requireContext())
                    .setTitle("删除任务")
                    .setMessage("确定要删除这个任务吗？")
                    .setPositiveButton("删除") { _, _ ->
                        lifecycleScope.launch {
                            repository.deleteTodo(todo)
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        )

        binding.rvTodos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = todoAdapter
        }

        binding.btnAddTodo.setOnClickListener {
            showAddTodoDialog()
        }
        binding.etNewTodo.setOnEditorActionListener { _, _, _ ->
            showAddTodoDialog()
            true
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getAllTodos().collect { todos ->
                    todoAdapter.submitList(todos)
                    binding.layoutEmpty.visibility = if (todos.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvTodos.visibility = if (todos.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun showAddTodoDialog() {
        val initial = binding.etNewTodo.text.toString().trim()
        binding.etNewTodo.setText("")

        val titleInput = EditText(requireContext()).apply {
            hint = "任务内容"
            setText(initial)
        }
        val cbDaily = CheckBox(requireContext()).apply {
            text = "每日提醒"
        }
        val tvTime = object : android.widget.TextView(requireContext()) {
            var selectedTime: String = ""

            init {
                text = "点击选择时间"
                setPadding(0, 8, 0, 8)
                textSize = 14f
            }
        }
        tvTime.setOnClickListener {
            val now = java.util.Calendar.getInstance()
            TimePickerDialog(requireContext(), { _, hour, minute ->
                tvTime.selectedTime = String.format(java.util.Locale.getDefault(), "%02d:%02d", hour, minute)
                tvTime.text = "时间: ${tvTime.selectedTime}"
            }, now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), true).show()
        }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(titleInput)
            addView(tvTime)
            addView(cbDaily)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("编辑Todo")
            .setView(container)
            .setPositiveButton("确定") { _, _ ->
                val title = titleInput.text.toString().trim()
                if (title.isNotBlank()) {
                    val todo = Todo(
                        title = title,
                        time = tvTime.selectedTime,
                        dailyReminder = cbDaily.isChecked
                    )
                    lifecycleScope.launch {
                        repository.insertTodo(todo)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}