package com.miaodi.note.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.miaodi.note.data.model.Book
import com.miaodi.note.databinding.ItemBookBinding
import com.miaodi.note.R

class BookAdapter(
    private val onItemClick: (Book) -> Unit,
    private val onMenuClick: (Book, View) -> Unit,
    private val getSelectedId: () -> Long
) : ListAdapter<Book, BookAdapter.BookViewHolder>(BookDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BookViewHolder(private val binding: ItemBookBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
            binding.btnBookMenu.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onMenuClick(getItem(position), it)
                }
            }
        }

        fun bind(book: Book) {
            val selected = book.id == getSelectedId()
            binding.tvName.text = book.name
            val ctx = binding.root.context
            val card = binding.root as CardView
            // 所有书本卡片配色统一（不再区分选中/未选中背景）
            card.setCardBackgroundColor(ctx.getColor(R.color.primary))
            card.cardElevation = if (selected) 6f else 2f
            // 所有书本图标保持一致（白色），仅右下角可选中按钮变色
            binding.ivBookIcon.imageTintList =
                android.content.res.ColorStateList.valueOf(ctx.getColor(android.R.color.white))
            binding.tvName.setTextColor(ctx.getColor(android.R.color.white))
            // 右下角可选中按钮：选中为琥珀色勾选，未选中为白色圆环
            binding.ivSelectIndicator.setImageResource(
                if (selected) R.drawable.ic_select_on else R.drawable.ic_select_off
            )
        }
    }

    class BookDiffCallback : DiffUtil.ItemCallback<Book>() {
        override fun areItemsTheSame(oldItem: Book, newItem: Book): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Book, newItem: Book): Boolean {
            return oldItem == newItem
        }
    }
}