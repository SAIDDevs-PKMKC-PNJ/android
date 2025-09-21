package com.pkm.said

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.pkm.said.ArticleItem
import com.pkm.said.R
import com.pkm.said.databinding.ActivityArticleContentBinding

class ArticleContentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArticleContentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArticleContentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val article = intent.getParcelableExtra<ArticleItem>(EXTRA_ARTICLE)
        if (article == null) {
            finish()
            return
        }

        render(article)
        setupClicks(article)
    }

    private fun render(article: ArticleItem) = with(binding) {
        // Header
        tvBadge.text = when {
            article.category.isNotBlank() -> article.category.uppercase()
            else -> getString(R.string.HeadlineTemplate)
        }
        tvTitle.text = article.title
        tvDate.text = article.date

        // Hero image
        Glide.with(this@ArticleContentActivity)
            .load(article.imageUrl)
            .placeholder(R.drawable.placeholder_oval)
            .error(R.drawable.placeholder_oval)
            .into(ivHero)

        // Author
        tvAuthorName.text = if (article.source.isBlank()) getString(R.string.article_author_default) else article.source
        tvAuthorRole.text = getString(R.string.article_author_role)

        // Isi artikel
        val body = article.content.ifBlank { article.description }
            .ifBlank {
                // fallback lorem untuk demo tampilan
                getString(R.string.article_lorem_long)
            }
        tvContent.text = body
        tvContent.movementMethod = LinkMovementMethod.getInstance()
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

    companion object {
        const val EXTRA_ARTICLE = "extra_article"

        fun start(context: Context, article: ArticleItem) {
            val i = Intent(context, ArticleContentActivity::class.java)
            i.putExtra(EXTRA_ARTICLE, article)
            context.startActivity(i)
        }
    }
}