package com.pkm.said

// ✅ SHARED: Filtering logic
fun List<NewsArticle>.getRelevantStrokeArticles(): List<NewsArticle> {
    return this.filter { article ->
        val title = article.title?.lowercase() ?: ""
        val description = article.description?.lowercase() ?: ""
        val text = "$title $description"

        val hasStroke = text.contains("stroke")
        val hasNegative = listOf("heat stroke", "the strokes", "golf", "music")
            .any { text.contains(it, ignoreCase = true) }

        hasStroke && !hasNegative
    }
}

// ✅ SHARED: Converter logic
fun NewsArticle.toCommonArticleItem(): ArticleItem {
    val rawUrl = this.url.orEmpty()
    val domain = try {
        java.net.URI(rawUrl).host?.removePrefix("www.") ?: ""
    } catch (_: Exception) {
        ""
    }

    val stableId = when {
        rawUrl.isNotBlank() -> "url_${rawUrl.hashCode()}"
        !title.isNullOrBlank() -> "title_${title.hashCode()}"
        else -> "gen_${System.nanoTime()}"
    }

    val imageUrl = this.urlToImage?.takeIf {
        it.isNotBlank() && it.startsWith("http")
    } ?: ""

    val rawContent = content.orEmpty()
    val cleanedContent = if (rawContent.length > 200) {
        rawContent.substring(0, 200) + "..."
    } else {
        rawContent
    }

    val rawDescription = description.orEmpty()
    val cleanedDescription = if (rawDescription.length > 150) {
        rawDescription.substring(0, 150) + "..."
    } else {
        rawDescription
    }

    return ArticleItem(
        id = stableId,
        title = title.orEmpty(),
        date = publishedAt.orEmpty(),
        author = author.orEmpty(),
        source = source?.name.orEmpty(),
        imageUrl = imageUrl,
        category = "Headline",
        description = cleanedDescription,
        content = cleanedContent,
        url = rawUrl,
        domain = domain
    )
}

// ✅ SHARED: Categorization logic
data class CategorizedArticles(
    val headlines: List<ArticleItem>,
    val edukasi: List<ArticleItem>,
    val pencegahan: List<ArticleItem>
)

fun List<ArticleItem>.categorizeArticles(): CategorizedArticles {
    val headlines = this.take(5)
    val allArticles = this

    val edukasiKeywords = listOf(
        "gejala", "tanda", "deteksi", "edukasi", "jenis stroke",
        "iskemik", "hemoragik", "tia", "symptoms", "warning",
        "education", "fast", "befast", "wajah", "lengan", "bicara"
    )

    val pencegahanKeywords = listOf(
        "pencegahan", "cegah", "pengobatan", "rehabilitasi",
        "prevention", "treatment", "recovery", "tekanan darah",
        "kolesterol", "diabetes", "merokok", "olahraga", "diet"
    )

    val edukasi = allArticles.filter { article ->
        val text = "${article.title} ${article.description}".lowercase()
        edukasiKeywords.any { text.contains(it, ignoreCase = true) }
    }.distinctBy { it.id }.take(5)

    val pencegahan = allArticles.filter { article ->
        val text = "${article.title} ${article.description}".lowercase()
        pencegahanKeywords.any { text.contains(it, ignoreCase = true) }
    }.distinctBy { it.id }.take(5)

    return CategorizedArticles(headlines, edukasi, pencegahan)
}

// ✅ SHARED: Placeholder generator
fun generatePlaceholderArticles(count: Int, category: String): List<ArticleItem> {
    return (1..count).map { id ->
        ArticleItem(
            id = "ph-$category-$id",
            title = when (category) {
                "Edukasi" -> when (id) {
                    1 -> "Mengenal Gejala Stroke dan Penanganannya"
                    2 -> "Apa Itu Stroke? Penyebab dan Jenis-Jenisnya"
                    3 -> "Metode FAST: Deteksi Dini Gejala Stroke"
                    4 -> "Perbedaan Stroke Iskemik dan Hemoragik"
                    else -> "Pemulihan Pasca Stroke: Yang Perlu Diketahui"
                }
                "Pencegahan" -> when (id) {
                    1 -> "Cara Mencegah Stroke dengan Gaya Hidup Sehat"
                    2 -> "Kontrol Tekanan Darah untuk Cegah Stroke"
                    3 -> "Pola Makan Sehat untuk Menurunkan Risiko Stroke"
                    4 -> "Olahraga Teratur sebagai Pencegahan Stroke"
                    else -> "Hindari Rokok dan Alkohol untuk Cegah Stroke"
                }
                else -> when (id) {
                    1 -> "Update Terkini Tentang Penanganan Stroke"
                    2 -> "Perkembangan Terbaru dalam Terapi Stroke"
                    else -> "Inovasi Pengobatan Stroke di Indonesia"
                }
            },
            date = "—",
            source = "SAID - Edukasi Kesehatan",
            imageUrl = "",
            category = category,
            description = when (category) {
                "Edukasi" -> "Artikel edukasi tentang pencegahan, gejala, dan penanganan stroke."
                "Pencegahan" -> "Panduan lengkap untuk mencegah stroke melalui gaya hidup sehat."
                else -> "Informasi terkini seputar perkembangan penanganan stroke."
            },
            content = "Konten lengkap sedang dalam proses pengumpulan data...",
            url = "",
            author = "Tim SAID",
            domain = "said.app"
        )
    }
}