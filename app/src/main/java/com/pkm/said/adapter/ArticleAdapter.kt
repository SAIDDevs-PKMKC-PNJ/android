package com.pkm.said.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.pkm.said.ArticleItem
import com.pkm.said.databinding.ItemArticleRowBinding

class ArticleListAdapter(
    private val onClick: (ArticleItem) -> Unit
) : ListAdapter<ArticleItem, ArticleListAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ArticleItem>() {
            override fun areItemsTheSame(oldItem: ArticleItem, newItem: ArticleItem) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: ArticleItem, newItem: ArticleItem) = oldItem == newItem
        }
    }

    inner class VH(val binding: ItemArticleRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ArticleItem) {
            Glide.with(binding.root).load(item.imageUrl).into(binding.ivThumb)
            binding.tvTitle.text = item.title
            binding.tvMeta.text = "${item.date}   •   ${item.source}"
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemArticleRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}