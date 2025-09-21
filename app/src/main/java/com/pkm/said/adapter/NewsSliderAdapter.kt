package com.pkm.said.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.pkm.said.ArticleItem
import com.pkm.said.databinding.ItemNewsSliderBinding

class NewsSliderAdapter(
    private val onClick: (ArticleItem) -> Unit
) : RecyclerView.Adapter<NewsSliderAdapter.VH>() {

    private val items = mutableListOf<ArticleItem>()

    fun submitList(list: List<ArticleItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemNewsSliderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ArticleItem) {
            Glide.with(binding.root).load(item.imageUrl).into(binding.ivCover)
            binding.tvBadge.text = item.category.uppercase()
            binding.tvTitle.text = item.title
            binding.card.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemNewsSliderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size
}