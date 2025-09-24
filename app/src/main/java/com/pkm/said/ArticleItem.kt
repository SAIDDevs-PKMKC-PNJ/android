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
    val content: String = ""
) : Parcelable

fun NewsArticle.toArticleItem(): ArticleItem =
    ArticleItem(
        id = (url ?: title ?: System.nanoTime().toString()),
        title = title.orEmpty(),
        date = publishedAt.orEmpty(),
        author.orEmpty(),
        source = source?.name.orEmpty(),
        imageUrl = urlToImage.orEmpty(),
        category = "Health",
        description = description.orEmpty(),
        content = content.orEmpty()
    )