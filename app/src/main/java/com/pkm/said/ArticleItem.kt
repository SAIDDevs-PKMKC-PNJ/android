package com.pkm.said

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ArticleItem(
    val id: Int,
    val title: String,
    val date: String,
    val source: String,
    val imageUrl: String,
    val category: String,
    val description: String = "",
    val content: String = ""
) : Parcelable