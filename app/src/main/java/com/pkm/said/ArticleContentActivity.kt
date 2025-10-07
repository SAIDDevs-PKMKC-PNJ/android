package com.pkm.said

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.pkm.said.databinding.ActivityArticleContentBinding

class ArticleContentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArticleContentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArticleContentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val json = intent.getStringExtra(KEY_JSON)
        val article = try {
            Gson().fromJson(json, ArticleItem::class.java)
        } catch (e: Exception) {
            null
        }

        if (article == null || article.title.isEmpty()) {
            handleNullArticle()
            return
        }

        bindArticle(article)
        setupClickListeners(article)
    }

//    private fun initializeArticle() {
//        val json = intent.getStringExtra(KEY_JSON)
//        val article = try {
//            Gson().fromJson(json, ArticleItem::class.java)?.takeIf {
//                it.title.isNotEmpty()
//            }
//        } catch (e: JsonSyntaxException) {
//            null
//        } catch (e: IllegalArgumentException) {
//            null
//        }
//
//        if (article == null) {
//            handleNullArticle()
//            return
//        }
//
//        bindArticle(article)
//        setupClickListeners(article)
//    }

    private fun bindArticle(article: ArticleItem) {
        binding.tvTitle.text = article.title
        binding.tvDate.text = article.date

        // Use author field instead of source for author name
        binding.tvAuthorName.text = article.author.ifBlank {
            getString(R.string.article_author_default)
        }
        binding.tvAuthorRole.text = getString(R.string.article_author_role)

        val content = when {
            article.content.isNotBlank() -> article.content
            article.description.isNotBlank() -> article.description
            else -> getString(R.string.article_lorem_long)
        }
        binding.tvContent.text = content

        binding.tvBadge.text = if (article.category.isNotBlank()) {
            article.category.uppercase()
        } else {
            getString(R.string.HeadlineTemplate)
        }

        Glide.with(this)
            .load(article.imageUrl)
            .placeholder(R.drawable.placeholder)
            .error(R.drawable.placeholder_oval)
            .into(binding.ivHero)
    }

    private fun setupClickListeners(article: ArticleItem) {
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.fabBookmark.setOnClickListener {
            showToast(getString(R.string.bookmarked_msg))
        }

        binding.fabShare.setOnClickListener {
            shareArticle(article)
        }
    }

    private fun shareArticle(article: ArticleItem) {
        val preview = when {
            article.description.isNotBlank() -> article.description
            article.content.isNotBlank() -> article.content.take(160)
            else -> ""
        }
        val shareText = buildString {
            append(article.title)
            append("\n\n")
            append(preview)
            // Include URL if available
            if (article.url.isNotBlank()) {
                append("\n\n")
                append(article.url)
            }
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, article.title)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }

        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_article)))
    }

    private fun handleNullArticle() {
        showToast("Artikel tidak ditemukan")

        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "dashboard")
        }.also { startActivity(it) }

        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val KEY_JSON = "article_json"

        fun start(context: Context, article: ArticleItem) {
            val intent = Intent(context, ArticleContentActivity::class.java).apply {
                putExtra(KEY_JSON, Gson().toJson(article))
            }
            context.startActivity(intent)
        }
    }
}