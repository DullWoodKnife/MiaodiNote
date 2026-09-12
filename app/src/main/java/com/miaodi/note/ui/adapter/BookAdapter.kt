package com.miaodi.note.ui.adapter

import android.view.LayoutInflater
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
        }

        fun bind(book: Book) {
            val selected = book.id == getSelectedId()
            binding.tvName.text = book.name
            val card = binding.root as CardView
            if (selected) {
                card.setCardBackgroundColor(binding.root.context.getColor(R.color.primary))
                binding.tvName.setTextColor(binding.root.context.getColor(android.R.color.white))
                card.cardElevation = 6f
            } else {
                card.setCardBackgroundColor(binding.root.context.getColor(R.color.primary_light))
                binding.tvName.setTextColor(binding.root.context.getColor(R.color.on_surface))
                card.cardElevation = 2f
            }
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