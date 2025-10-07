package com.pkm.said

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
)
