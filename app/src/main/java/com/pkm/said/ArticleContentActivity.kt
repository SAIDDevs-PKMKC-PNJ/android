package com.pkm.said

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.pkm.said.databinding.ActivityArticleContentBinding

class ArticleContentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArticleContentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArticleContentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val article = intent.getParcelableExtra<ArticleItem>("article")
        if (article != null) {
            bindArticle(article)
            setupClicks(article)
        } else {
            handleNullArticle()
            return
        }
    }

    private fun bindArticle(article: ArticleItem) {
        binding.tvTitle.text = article.title
        binding.tvDate.text = article.date
        binding.tvAuthorName.text = if (article.source.isBlank()) getString(R.string.article_author_default) else article.source
        binding.tvAuthorRole.text = getString(R.string.article_author_role)
        binding.tvContent.text = article.content.ifBlank { article.description }
            .ifBlank {
                // fallback lorem untuk demo tampilan
                getString(R.string.article_lorem_long)
            }
        binding.tvBadge.text = when {
            article.category.isNotBlank() -> article.category.uppercase()
            else -> getString(R.string.HeadlineTemplate)
        }

        Glide.with(this)
            .load(article.imageUrl)
            .placeholder(R.drawable.placeholder)
            .error(R.drawable.placeholder_oval)
            .into(binding.ivHero)
    }

    private fun setupClicks(article: ArticleItem) = with(binding) {
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        fabBookmark.setOnClickListener {
            // TODO: Simpan ke bookmark (Room/Firestore). Untuk sekarang, tampilkan feedback
            showSnackbar(getString(R.string.bookmarked_msg))
        }

        fabShare.setOnClickListener {
            shareArticle(article)
        }
    }

    private fun showSnackbar(message: String) {
        // Gunakan Material Snackbar jika mau; sementara pakai Toast
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun shareArticle(article: ArticleItem) {
        val shareText = "${article.title}\n\n${article.description.ifBlank { article.content.take(160) }}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, article.title)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_article)))
    }

    private fun handleNullArticle() {
        Toast.makeText(this, "Artikel tidak ditemukan", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "dashboard")
        }
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_ARTICLE = "extra_article"

        fun start(context: Context, article: ArticleItem) {
            val i = Intent(context, ArticleContentActivity::class.java)
            i.putExtra(EXTRA_ARTICLE, article)
            context.startActivity(i)
        }
    }
}