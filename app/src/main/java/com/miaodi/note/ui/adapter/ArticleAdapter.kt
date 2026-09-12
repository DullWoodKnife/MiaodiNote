package com.miaodi.note.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.miaodi.note.data.model.Article
import com.miaodi.note.databinding.ItemArticleBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ArticleAdapter(
    private val onItemClick: (Article) -> Unit,
    private val onItemLongClick: (Article) -> Unit,
    private val onSelectionChanged: () -> Unit
) : ListAdapter<Article, ArticleAdapter.ArticleViewHolder>(ArticleDiffCallback()) {

    private val selectedIds = mutableSetOf<Long>()
    var selectionMode: Boolean = false
        set(value) {
            field = value
            if (!value) selectedIds.clear()
            notifyDataSetChanged()
        }

    val selectedCount: Int get() = selectedIds.size

    fun isSelected(article: Article): Boolean = selectedIds.contains(article.id)

    fun toggleSelection(article: Article) {
        if (!selectionMode) selectionMode = true
        if (!selectedIds.remove(article.id)) {
            selectedIds.add(article.id)
        }
        notifyItemChanged(currentList.indexOf(article))
        onSelectionChanged()
    }

    fun resetSelection() {
        selectionMode = false
        onSelectionChanged()
    }

    fun getSelectedArticles(): List<Article> {
        return currentList.filter { selectedIds.contains(it.id) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleViewHolder {
        val binding = ItemArticleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ArticleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ArticleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ArticleViewHolder(private val binding: ItemArticleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        companion object {
            private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        }

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val article = getItem(position)
                    if (selectionMode) {
                        toggleSelection(article)
                    } else {
                        onItemClick(article)
                    }
                }
            }
            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val article = getItem(position)
                    if (!selectionMode) {
                        selectionMode = true
                        toggleSelection(article)
                    }
                    true
                } else {
                    false
                }
            }
            binding.cbSelect.setOnCheckedChangeListener { _, isChecked ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val article = getItem(position)
                    if (isChecked) {
                        selectedIds.add(article.id)
                    } else {
                        selectedIds.remove(article.id)
                    }
                    onSelectionChanged()
                }
            }
        }

        fun bind(article: Article) {
            binding.tvTitle.text = article.title.ifBlank { "未命名" }
            binding.tvPreview.text = article.content.take(100).ifBlank { "暂无内容" }
            binding.tvDate.text = dateFormat.format(Date(article.updatedAt))
            binding.tvMdBadge.visibility = if (article.isMarkdown) android.view.View.VISIBLE else android.view.View.GONE
            binding.tvNewBadge.visibility = if (article.isNew) android.view.View.VISIBLE else android.view.View.GONE
            binding.vStatusDot.background?.setTint(article.statusColor)

            binding.cbSelect.visibility = if (selectionMode) android.view.View.VISIBLE else android.view.View.GONE
            binding.cbSelect.isChecked = selectedIds.contains(article.id)
            binding.root.isActivated = selectionMode && selectedIds.contains(article.id)
            binding.root.alpha = if (selectionMode && selectedIds.contains(article.id)) 1f else if (selectionMode) 0.6f else 1f
            binding.cbSelect.setOnCheckedChangeListener(null)
            binding.cbSelect.isChecked = selectedIds.contains(article.id)
            binding.cbSelect.setOnCheckedChangeListener { _, isChecked ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val a = getItem(position)
                    if (isChecked) selectedIds.add(a.id) else selectedIds.remove(a.id)
                    binding.root.isActivated = selectedIds.contains(a.id)
                    binding.root.alpha = if (selectedIds.contains(a.id)) 1f else 0.6f
                    onSelectionChanged()
                }
            }
        }
    }

    class ArticleDiffCallback : DiffUtil.ItemCallback<Article>() {
        override fun areItemsTheSame(oldItem: Article, newItem: Article): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Article, newItem: Article): Boolean {
            return oldItem == newItem
        }
    }
}