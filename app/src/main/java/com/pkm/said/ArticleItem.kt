package com.pkm.said

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ArticleItem(
    val id: String,
    val title: String,
    val date: String,
    val author: String,
    val source: String,
    val imageUrl: String,
    val category: String,
    val description: String = "",
    val content: String = "",

    val url: String = "",       // link asli artikel
    val domain: String = "",    // host/domain (kompas.com, dll.)
    val language: String? = null,
    val sourceId: String? = null
) : Parcelable

fun NewsArticle.toArticleItem(): ArticleItem {
    val rawUrl = this.url.orEmpty()
    val domain = try {
        java.net.URI(rawUrl).host?.removePrefix("www.") ?: ""
    } catch (_: Exception) { "" }

    // bikin id stabil dari url (kalau ada), fallback ke title, terakhir nanoTime
    val stableId = when {
        rawUrl.isNotBlank() -> rawUrl.hashCode().toString()
        !title.isNullOrBlank() -> title.hashCode().toString()
        else -> System.nanoTime().toString()
    }

    return ArticleItem(
        id = stableId,
        title = title.orEmpty(),
        date = publishedAt.orEmpty(),
        author = author.orEmpty(),
        source = source?.name.orEmpty(),
        imageUrl = urlToImage.orEmpty(),
        category = "Health",                 // atau biarkan diisi saat klasifikasi
        description = description.orEmpty(),
        content = content.orEmpty(),
        url = rawUrl,
        domain = domain,
        language = this.language,           // kalau field ini ada di NewsArticle
        sourceId = source?.id
    )
}
