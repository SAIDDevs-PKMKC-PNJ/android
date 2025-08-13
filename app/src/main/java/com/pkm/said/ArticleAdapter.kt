package com.pkm.said

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pkm.said.databinding.ItemArticleCardBinding

class ArticleAdapter(
    private val articles: List<Article>,
    private val onItemClick: (Article) -> Unit = {}
) : RecyclerView.Adapter<ArticleAdapter.ArticleViewHolder>() {

    companion object {
        private const val TAG = "ArticleAdapter"
    }

    inner class ArticleViewHolder(
        private val binding: ItemArticleCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(article: Article) {
            try {
                // ✅ VIEW BINDING - Set text data
                binding.apply {
                    tvArticleTitle.text = article.title
                    tvArticleDate.text = article.date
                    tvArticleSource.text = article.source

                    // ✅ OPTIONAL - Set category if available in layout
//                    try {
//                        tvArticleCategory?.text = article.category
//                    } catch (e: Exception) {
//                        // Category view might not exist in layout
//                        Log.d(TAG, "Category view not found in layout")
//                    }
                }

                // ✅ ENHANCED - Image loading with Glide (if available)
                loadArticleImage(article)

                // ✅ IMPROVED - Click listener with better error handling
                binding.root.setOnClickListener {
                    try {
                        Log.d(TAG, "Article clicked: ${article.title}")
                        onItemClick(article)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error handling article click", e)
                    }
                }

                Log.d(TAG, "✅ Article bound: ${article.title}")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error binding article: ${article.title}", e)
            }
        }

        // ✅ ENHANCED - Better image loading
        private fun loadArticleImage(article: Article) {
            try {
                when {
                    // If imageUrl is provided and not empty
                    article.imageUrl.isNotEmpty() && article.imageUrl.startsWith("http") -> {
                        // TODO: Add Glide dependency for remote image loading
                        // For now, use placeholder
                        binding.ivArticleImage.setImageResource(R.drawable.placeholder_image)

                        /* When Glide is added, use this:
                        Glide.with(binding.root.context)
                            .load(article.imageUrl)
                            .placeholder(R.drawable.placeholder_image)
                            .error(R.drawable.placeholder_error)
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .into(binding.ivArticleImage)
                        */
                    }

                    // Category-based placeholder images
                    article.category == "Edukasi" -> {
                        binding.ivArticleImage.setImageResource(R.drawable.placeholder_image)
                    }

                    article.category == "Pencegahan" -> {
                        binding.ivArticleImage.setImageResource(R.drawable.placeholder_image)
                    }

                    // Default placeholder
                    else -> {
                        binding.ivArticleImage.setImageResource(R.drawable.placeholder_image)
                    }
                }

                // ✅ ACCESSIBILITY - Set content description
                binding.ivArticleImage.contentDescription = "Gambar artikel: ${article.title}"

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading image for article: ${article.title}", e)
                binding.ivArticleImage.setImageResource(R.drawable.placeholder_image)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleViewHolder {
        Log.d(TAG, "Creating ViewHolder for position: $viewType")

        return try {
            val binding = ItemArticleCardBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            ArticleViewHolder(binding)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating ViewHolder", e)
            throw e
        }
    }

    override fun onBindViewHolder(holder: ArticleViewHolder, position: Int) {
        try {
            if (position < articles.size) {
                holder.bind(articles[position])
                Log.d(TAG, "✅ ViewHolder bound at position: $position")
            } else {
                Log.w(TAG, "⚠️ Invalid position: $position, articles size: ${articles.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error binding ViewHolder at position: $position", e)
        }
    }

    override fun getItemCount(): Int {
        val count = articles.size
        Log.d(TAG, "📊 Article count: $count")
        return count
    }

    // ✅ UTILITY - Get article at position (helpful for debugging)
    fun getArticleAt(position: Int): Article? {
        return if (position in 0 until articles.size) {
            articles[position]
        } else {
            Log.w(TAG, "⚠️ Invalid position: $position")
            null
        }
    }

    // ✅ UTILITY - Update data if needed (for future use)
    fun updateData(newArticles: List<Article>) {
        // This would be used if you implement DiffUtil later
        Log.d(TAG, "Data update requested: ${newArticles.size} articles")
        // For now, just log - full implementation would use DiffUtil
    }
}