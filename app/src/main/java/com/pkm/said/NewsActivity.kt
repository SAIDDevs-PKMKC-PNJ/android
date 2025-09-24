package com.pkm.said

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayoutMediator
import com.pkm.said.adapter.ArticleListAdapter
import com.pkm.said.adapter.NewsSliderAdapter
import com.pkm.said.databinding.ActivityNewsBinding
import com.pkm.said.service.NewsRetrofit
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class NewsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "NewsActivity"
        private const val AUTO_SCROLL_INTERVAL = 4000L
    }

    private lateinit var binding: ActivityNewsBinding
    private var tabMediator: TabLayoutMediator? = null
    private var firstHeadlinesShown = false


    private lateinit var sliderAdapter: NewsSliderAdapter
    private lateinit var edukasiAdapter: ArticleListAdapter
    private lateinit var pencegahanAdapter: ArticleListAdapter

    // --- Debounce klik ---
    private var lastClickAt = 0L
    private fun safeClick(action: () -> Unit) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastClickAt < 500) return
        lastClickAt = now
        action()
    }

    // --- Auto-scroll slider ---
    private val autoScrollHandler = Handler(Looper.getMainLooper())
    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            val itemCount = sliderAdapter.itemCount
            if (itemCount > 1) {
                val next = (binding.viewPager.currentItem + 1) % itemCount
                binding.viewPager.setCurrentItem(next, true)
            }
            autoScrollHandler.postDelayed(this, AUTO_SCROLL_INTERVAL)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "=== NEWS ACTIVITY DEBUG === onCreate")

        setupToolbar()
        setupSlider()
        setupLists()
        loadDataFromApi() // << gunakan API, bukan dummy
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.tvTitle.text = getString(R.string.news_title_stroke_world)
    }

    private fun setupSlider() {
        sliderAdapter = NewsSliderAdapter { article ->
            safeClick { openDetail(article) }
        }
        binding.viewPager.adapter = sliderAdapter

        val pageOffset = resources.getDimensionPixelSize(R.dimen.slider_page_offset)
        binding.viewPager.setPageTransformer { page, position ->
            page.translationX = -pageOffset * position
            page.scaleY = 0.92f + (1 - kotlin.math.abs(position)) * 0.08f
        }

        attachMediator()
    }

    private fun attachMediator() {
        tabMediator?.detach()
        tabMediator = TabLayoutMediator(binding.tabDots, binding.viewPager) { _, _ -> }
        tabMediator?.attach()
    }


    private fun setupLists() {
        edukasiAdapter = ArticleListAdapter { article ->
            safeClick { openDetail(article) }
        }
        pencegahanAdapter = ArticleListAdapter { article ->
            safeClick { openDetail(article) }
        }

        binding.rvEdukasi.apply {
            layoutManager = LinearLayoutManager(this@NewsActivity)
            adapter = edukasiAdapter
            setHasFixedSize(true)
            isNestedScrollingEnabled = false
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            visibility = View.VISIBLE
        }
        binding.rvPencegahan.apply {
            layoutManager = LinearLayoutManager(this@NewsActivity)
            adapter = pencegahanAdapter
            setHasFixedSize(true)
            isNestedScrollingEnabled = false
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            visibility = View.VISIBLE
        }

        binding.rvEdukasi.visibility = View.VISIBLE
        binding.rvPencegahan.visibility = View.VISIBLE
    }

    private fun showLoading(show: Boolean) {
        val alpha = if (show) 0.4f else 1f
        binding.viewPager.alpha = alpha
        binding.rvEdukasi.alpha = alpha
        binding.rvPencegahan.alpha = alpha
    }

    // ======================
    // API
    // ======================
    private fun loadDataFromApi() {
        val apiKey = BuildConfig.NEWS_API_KEY
        val q = "stroke"

        showLoading(true)

        NewsRetrofit.api.searchEverything(
            q = q,
            language = "id",
            sortBy = "publishedAt",
            page = 1,
            pageSize = 40,
            apiKey = apiKey
        ).enqueue(object : Callback<NewsResponse> {
            override fun onResponse(call: Call<NewsResponse>, response: Response<NewsResponse>) {
                if (!response.isSuccessful) {
                    fetchFallbackEn(q, apiKey)
                    return
                }
                val idArticles = response.body()?.articles.orEmpty()
                if (idArticles.isEmpty()) {
                    fetchFallbackEn(q, apiKey)
                    return
                }
                bindArticlesToUi(idArticles)
            }

            override fun onFailure(call: Call<NewsResponse>, t: Throwable) {
                fetchFallbackEn(q, apiKey)
            }
        })
    }

    private fun fetchFallbackEn(q: String, apiKey: String) {
        NewsRetrofit.api.searchEverything(
            q = q,
            language = "en",
            sortBy = "publishedAt",
            page = 1,
            pageSize = 40,
            apiKey = apiKey
        ).enqueue(object : Callback<NewsResponse> {
            override fun onResponse(call: Call<NewsResponse>, response: Response<NewsResponse>) {
                if (!response.isSuccessful) {
                    showLoading(false)
                    Toast.makeText(
                        this@NewsActivity,
                        "Gagal memuat berita (${response.code()})",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                val enArticles = response.body()?.articles.orEmpty()
                if (enArticles.isEmpty()) {
                    showLoading(false)
                    Toast.makeText(this@NewsActivity, "Tidak ada hasil berita", Toast.LENGTH_SHORT)
                        .show()
                    return
                }
                bindArticlesToUi(enArticles)
            }

            override fun onFailure(call: Call<NewsResponse>, t: Throwable) {
                showLoading(false)
                Toast.makeText(this@NewsActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // ====== Helper placeholder ======
    private fun placeholderArticle(id: Int, category: String) = ArticleItem(
        id = "ph-$category-$id",
        title = when (category) {
            "Edukasi" -> "Apa itu Stroke? Kenali Gejala FAST"
            "Pencegahan" -> "5 Langkah Sederhana Cegah Stroke"
            else -> "Update Seputar Stroke"
        },
        date = "—",
        source = "SAID",
        imageUrl = "", // biar pakai placeholder image di Glide
        category = category,
        description = "",
        author = "",
        content = ""
    )

    private fun ensureSize(targetSize: Int, base: List<ArticleItem>, category: String): List<ArticleItem> {
        if (base.size >= targetSize) return base.take(targetSize)
        val result = base.toMutableList()
        var i = 0
        // 1) Daur ulang item yang ada
        while (result.size < targetSize && base.isNotEmpty()) {
            result += base[i % base.size].copy(id = "${base[i % base.size].id}-dup-${i}", category = category)
            i++
        }
        // 2) Tambal placeholder kalau masih kurang (atau base kosong)
        var ph = 0
        while (result.size < targetSize) {
            result += placeholderArticle(++ph, category)
        }
        return result
    }


    private fun bindArticlesToUi(raw: List<NewsArticle>) {
        showLoading(false)

        // Map ke ArticleItem
        Log.d("NewsActivity", "raw=${raw.size}")
        val mapped = raw.map { it.toArticleItem() }
        Log.d("NewsActivity", "mapped=${mapped.size}")

        if (mapped.isEmpty()) {
            val headlines = (1..3).map { placeholderArticle(it, "Headline") }
            val edu       = (1..5).map { placeholderArticle(it, "Edukasi") }
            val prev      = (1..5).map { placeholderArticle(it, "Pencegahan") }
            sliderAdapter.submitList(headlines)
            edukasiAdapter.submitList(edu)
            pencegahanAdapter.submitList(prev)
            return
        }

        val headlines = mapped.take(5).map { it.copy(category = "Headline") }
        Log.d("NewsActivity", "headlines=${headlines.size}")
        sliderAdapter.submitList(headlines)

        if (!firstHeadlinesShown) {
            binding.viewPager.post { attachMediator() }
            firstHeadlinesShown = true
        }

        // Kategori sederhana via keyword
        val edukasiKeywords = listOf(
            // ID
            "apa itu", "jenis", "gejala", "definisi", "penyebab", "edukasi", "fakta", "panduan",
            "iskemik", "hemoragik", "stroke iskemik", "stroke hemoragik", "fast",
            // EN
            "what is", "types", "symptoms", "definition", "causes", "education", "facts", "guide",
            "ischemic", "hemorrhagic", "tia", "transient ischemic attack", "signs", "recognize"
        )

        val pencegahanKeywords = listOf(
            // ID
            "pencegahan", "mencegah", "tips", "gaya hidup", "diet", "olahraga", "kebiasaan",
            "kontrol tekanan darah", "kurangi garam", "berhenti merokok", "turunkan risiko",
            // EN
            "prevention", "prevent", "tips", "lifestyle", "diet", "exercise", "habits",
            "blood pressure control", "low sodium", "quit smoking", "reduce risk", "risk reduction"
        )

        // 2) Normalizer: lowercase, hapus diakritik & tanda baca, compress whitespace
        fun normalize(s: String): String {
            val lower = s.lowercase()
            // remove diacritics
            val noDia = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
                .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            // remove punctuation
            val noPunct = noDia.replace("[\\p{Punct}]".toRegex(), " ")
            return noPunct.replace("\\s+".toRegex(), " ").trim()
        }

        // 3) Pencocokan dengan boundary sederhana (contains biasa bisa false positive)
        fun containsAny(text: String, keys: List<String>): Boolean {
            val t = " ${normalize(text)} " // padding spasi untuk pseudo-boundary
            return keys.any { key ->
                val k = " ${normalize(key)} "
                t.contains(k)
            }
        }

        // 4) Mapping + klasifikasi
        val normalizedArticles = mapped.map { art ->
            val blob = listOfNotNull(art.title, art.description, art.content).joinToString(" ")
            val isEdu = containsAny(blob, edukasiKeywords)
            val isPrev = containsAny(blob, pencegahanKeywords)
            when {
                isEdu && !isPrev -> art.copy(category = "Edukasi")
                !isEdu && isPrev -> art.copy(category = "Pencegahan")
                isEdu && isPrev -> art.copy(category = "Edukasi") // tie-break
                else -> art // kategori tetap (mis. "Headline" utk slider)
            }
        }

        // 5) Split + fallback kalau kosong
        val rest = normalizedArticles.drop(headlines.size)
        val edukasiRaw = rest.filter { it.category == "Edukasi" } +
                mapped.filter { it.category == "Edukasi" } // cadangan dari mapped awal
        val pencegahanRaw = rest.filter { it.category == "Pencegahan" } +
                mapped.filter { it.category == "Pencegahan" }

        val edukasiList = ensureSize(targetSize = 5, base = edukasiRaw, category = "Edukasi")
        val pencegahanList = ensureSize(targetSize = 5, base = pencegahanRaw, category = "Pencegahan")

        Log.d("NewsActivity", "edukasi=${edukasiList.size} pencegahan=${pencegahanList.size}")

        // fallback sederhana agar UI tidak kosong
        val fallbackEdukasi = edukasiList.ifEmpty { normalizedArticles.drop(5).take(6).map { it.copy(category = "Edukasi") } }

        val fallbackPencegahan =
            pencegahanList.ifEmpty {
                normalizedArticles.drop(5 + fallbackEdukasi.size).take(6)
                    .map { it.copy(category = "Pencegahan") }
            }

        // submit ke adapter
        edukasiAdapter.submitList(fallbackEdukasi) {
            Log.d(TAG, "rvEdukasi itemCount=${edukasiAdapter.itemCount}")
            binding.rvEdukasi.adapter = edukasiAdapter
            edukasiAdapter.notifyDataSetChanged()
        }
        pencegahanAdapter.submitList(fallbackPencegahan) {
            Log.d(TAG, "rvPencegahan itemCount=${pencegahanAdapter.itemCount}")
            binding.rvPencegahan.adapter = pencegahanAdapter
            pencegahanAdapter.notifyDataSetChanged()
        }
    }

    // Mapper API -> UI
    private fun NewsArticle.toArticleItem(category: String = "Headline"): ArticleItem =
        ArticleItem(
            id = this.url ?: this.title ?: System.nanoTime().toString(),
            title = this.title.orEmpty(),
            date = this.publishedAt.orEmpty(),
            source = this.source?.name.orEmpty(),
            imageUrl = this.urlToImage.orEmpty(),
            category = category,
            description = this.description.orEmpty(),
            author = this.author.orEmpty(),
            content = this.content.orEmpty()
        )

    // ======================
    // Navigation
    // ======================
    private fun openDetail(article: ArticleItem) {
        val intent = Intent(this, ArticleContentActivity::class.java).apply {
            putExtra("article", article)
        }
        startActivity(intent)
    }

    // ======================
    // Lifecycle
    // ======================
    override fun onResume() {
        super.onResume()
        autoScrollHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_INTERVAL)
    }

    override fun onPause() {
        super.onPause()
        autoScrollHandler.removeCallbacks(autoScrollRunnable)
    }

    override fun onDestroy() {
        autoScrollHandler.removeCallbacks(autoScrollRunnable)
        super.onDestroy()
    }
}
