package com.pkm.said.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.pkm.said.R
import com.pkm.said.databinding.ItemArticleCardBinding
import com.pkm.said.ArticleItem

class NewsAdapter(
    private val data: List<ArticleItem>,
    private val onClick: (ArticleItem) -> Unit
) : RecyclerView.Adapter<NewsAdapter.NewsVH>() {

    inner class NewsVH(val binding: ItemArticleCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ArticleItem) {
            binding.tvArticleTitle.text = item.title
            Glide.with(binding.root).load(item.imageUrl)
                .placeholder(R.drawable.placeholder)
                .error(R.drawable.placeholder)
                .into(binding.ivArticleImage)
            binding.tvArticleDate.text = item.date
            binding.tvArticleSource.text = item.source
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