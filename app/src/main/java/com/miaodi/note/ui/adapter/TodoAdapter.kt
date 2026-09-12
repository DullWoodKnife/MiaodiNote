package com.miaodi.note.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.miaodi.note.data.model.Todo
import com.miaodi.note.databinding.ItemTodoBinding

class TodoAdapter(
    private val onToggle: (Todo) -> Unit,
    private val onDelete: (Todo) -> Unit
) : ListAdapter<Todo, TodoAdapter.TodoViewHolder>(TodoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val binding = ItemTodoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TodoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TodoViewHolder(private val binding: ItemTodoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val todo = getItem(position)
                    onToggle(todo.copy(completed = !todo.completed))
                }
            }
            binding.btnDelete.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDelete(getItem(position))
                }
            }
        }

        fun bind(todo: Todo) {
            binding.tvTitle.text = todo.title.ifBlank { "未命名任务" }
            binding.tvTitle.paint.isStrikeThruText = todo.completed
            binding.tvTitle.alpha = if (todo.completed) 0.5f else 1f
            binding.cbComplete.isChecked = todo.completed
            binding.tvTime.text = todo.time.ifBlank { "未设置时间" }
            binding.tvTime.visibility = if (todo.time.isNotBlank()) android.view.View.VISIBLE else android.view.View.VISIBLE
            binding.tvDaily.visibility = if (todo.dailyReminder) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    class TodoDiffCallback : DiffUtil.ItemCallback<Todo>() {
        override fun areItemsTheSame(oldItem: Todo, newItem: Todo): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Todo, newItem: Todo): Boolean {
            return oldItem == newItem
        }
    }
}