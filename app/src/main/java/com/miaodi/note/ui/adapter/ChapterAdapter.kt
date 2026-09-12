package com.miaodi.note.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.miaodi.note.data.model.Chapter
import com.miaodi.note.databinding.ItemChapterBinding
import com.miaodi.note.R

class ChapterAdapter(
    private val onItemClick: (Chapter) -> Unit,
    private val getSelectedId: () -> Long
) : ListAdapter<Chapter, ChapterAdapter.ChapterViewHolder>(ChapterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterViewHolder {
        val binding = ItemChapterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChapterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChapterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChapterViewHolder(private val binding: ItemChapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(chapter: Chapter) {
            val selected = chapter.id == getSelectedId()
            binding.tvName.text = chapter.name
            val card = binding.root as CardView
            if (selected) {
                card.setCardBackgroundColor(binding.root.context.getColor(R.color.amber))
                binding.tvName.setTextColor(binding.root.context.getColor(android.R.color.white))
                card.cardElevation = 6f
            } else {
                card.setCardBackgroundColor(binding.root.context.getColor(R.color.chapter_light))
                binding.tvName.setTextColor(binding.root.context.getColor(R.color.on_surface))
                card.cardElevation = 2f
            }
        }
    }

    class ChapterDiffCallback : DiffUtil.ItemCallback<Chapter>() {
        override fun areItemsTheSame(oldItem: Chapter, newItem: Chapter): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Chapter, newItem: Chapter): Boolean {
            return oldItem == newItem
        }
    }
}