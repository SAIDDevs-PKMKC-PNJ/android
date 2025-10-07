package com.pkm.said

import java.net.URI

fun NewsArticle.toArticleItem(): ArticleItem {
    val rawUrl = this.url.orEmpty()
    val domain = try {
        URI(rawUrl).host?.removePrefix("www.") ?: ""
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