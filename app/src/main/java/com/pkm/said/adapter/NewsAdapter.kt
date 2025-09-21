package com.pkm.said.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pkm.said.R
import com.pkm.said.databinding.ItemArticleCardBinding
import com.pkm.said.NewsItem

class NewsAdapter(
    private val data: List<NewsItem>,
    private val onClick: (NewsItem) -> Unit
) : RecyclerView.Adapter<NewsAdapter.NewsVH>() {

    inner class NewsVH(val binding: ItemArticleCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: NewsItem) {
            binding.tvArticleTitle.text = item.title
            // Jika nanti pakai Glide/Picasso: load imageUrl
            binding.ivArticleImage.setImageResource(R.drawable.placeholder)
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsVH {
        val binding = ItemArticleCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NewsVH(binding)
    }

    override fun onBindViewHolder(holder: NewsVH, position: Int) = holder.bind(data[position])

    override fun getItemCount(): Int = data.size
}