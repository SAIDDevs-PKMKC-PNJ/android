package com.pkm.said

import org.intellij.lang.annotations.Language


data class NewsResponse(
    val status: String?,
    val totalResults: Int?,
    val articles: List<NewsArticle>?
)

data class NewsArticle(
    val source: NewsSource?,
    val author: String?,
    val title: String?,
    val description: String?,
    val domains: String?,
    val searchIn: String?,
    val language: String?,
    val url: String?,
    val urlToImage: String?,
    val publishedAt: String?,
    val content: String?
)

data class NewsSource(
    val id: String?,
    val name: String?
)
