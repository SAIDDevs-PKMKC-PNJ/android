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
import java.util.Locale

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
        setupLoadingState(true)
        loadDataFromApi()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupSlider() {
        sliderAdapter = NewsSliderAdapter { article ->
            safeClick { openDetail(article) }
        }
        binding.viewPager.adapter = sliderAdapter

        val pageOffset = resources.getDimensionPixelSize(R.dimen.slider_page_offset)

        // --- PERBAIKAN: PageTransformer BARU ---
        binding.viewPager.setPageTransformer { page, position ->
            val absPosition = kotlin.math.abs(position)

            // 1. Efek Zoom (Scale): Item aktif lebih besar (0.92f + 0.08f = 1.0f)
            // Item tidak aktif lebih kecil (0.92f + 0.08f * 0 = 0.92f)
            val scale = 0.92f + (1 - absPosition) * 0.08f
            page.scaleY = scale

            // 2. Efek Translasi (Offset)
            page.translationX = -pageOffset * position

            // 3. Efek ELEVATION (PENTING untuk mengatasi overlap/tumpang tindih)
            // Berikan elevasi tinggi pada item yang paling dekat dengan tengah (position=0)
            // agar ia digambar di atas item tetangganya.
            page.translationZ = (1f - absPosition) * 10f // Nilai 10f bisa disesuaikan
        }
        // --- AKHIR PERBAIKAN ---

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
        }
        binding.rvPencegahan.apply {
            layoutManager = LinearLayoutManager(this@NewsActivity)
            adapter = pencegahanAdapter
            setHasFixedSize(true)
            isNestedScrollingEnabled = false
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
        }
    }

    // ✅ PERBAIKAN: Satu fungsi loading saja
    private fun setupLoadingState(show: Boolean) {
        if (show) {
            binding.loadingOverlay.visibility = View.VISIBLE
            binding.loadingText.visibility = View.VISIBLE
            binding.rootScroll.visibility = View.GONE
        } else {
            binding.loadingOverlay.visibility = View.GONE
            binding.loadingText.visibility = View.GONE
            binding.rootScroll.visibility = View.VISIBLE
        }
    }

    // ======================
    // API - DIPERBAIKI
    // ======================
    private fun loadDataFromApi() {
        val apiKey = BuildConfig.NEWS_API_KEY

        // ✅ PERBAIKAN: Query lebih spesifik
        val q = "stroke (health OR symptoms OR prevention) -\"heat stroke\" -\"The Strokes\""

        val searchIn = "title,description"
        val deviceLang = Locale.getDefault().language
        val apiLang = if (deviceLang == "id") "id" else "en"

        Log.d(TAG, "🔍 Query: '$q'")
        Log.d(TAG, "🔍 Language: '$apiLang'")

        NewsRetrofit.api.searchEverything(
            q = q,
            language = apiLang,
            searchIn = searchIn,
            sortBy = "publishedAt",
            page = 1,
            pageSize = 50,
            apiKey = apiKey
        ).enqueue(object : Callback<NewsResponse> {
            override fun onResponse(call: Call<NewsResponse>, response: Response<NewsResponse>) {
                Log.d(TAG, "✅ Response code: ${response.code()}")

                if (response.isSuccessful) {
                    val articles = response.body()?.articles ?: emptyList()
                    Log.d(TAG, "📰 Raw articles received: ${articles.size}")

                    // ✅ DEBUG: Tampilkan judul dan gambar
                    articles.take(3).forEachIndexed { index, article ->
                        Log.d(TAG, "📄 Sample $index: ${article.title}")
                        Log.d(TAG, "   Image URL: ${article.urlToImage ?: "NULL"}")
                    }

                    if (articles.isNotEmpty()) {
                        processAndDisplayArticles(articles)
                    } else {
                        showFallbackContent()
                    }
                } else {
                    Log.w(TAG, "❌ API Error - Code: ${response.code()}")
                    showFallbackContent()
                }
            }

            override fun onFailure(call: Call<NewsResponse>, t: Throwable) {
                Log.e(TAG, "💥 API Failure - ${t.message}")
                showFallbackContent()
            }
        })
    }

    // ✅ PERBAIKAN: Keyword yang lebih efektif
    private val edukasiKeywords = listOf(
        "gejala", "tanda", "deteksi", "edukasi", "jenis",
        "iskemik", "hemoragik", "tia", "symptoms", "warning",
        "education", "fast", "befast", "face", "arm", "speech"
    )

    private val pencegahanKeywords = listOf(
        "pencegahan", "cegah", "pengobatan", "rehabilitasi",
        "prevention", "treatment", "recovery", "tekanan",
        "kolesterol", "diabetes", "merokok", "olahraga", "diet"
    )

    private fun processAndDisplayArticles(articles: List<NewsArticle>) {
        Log.d(TAG, "🔧 Processing ${articles.size} articles")

        // ✅ PERBAIKAN: Filter lebih longgar untuk testing
        val relevantArticles = articles.getRelevantStrokeArticles()
        Log.d(TAG, "Relevant articles after filtering: ${relevantArticles.size}")

        if (relevantArticles.isEmpty()) {
            showFallbackContent()
            return
        }

        // Convert to UI model
        val articleItems = relevantArticles.map { it.toCommonArticleItem() }
        val categorized = articleItems.categorizeArticles()

        // Tampilkan hasil
        displayCategorizedArticles(categorized)
        setupLoadingState(false)
    }

    // ✅ PERBAIKAN: Display logic dengan fallback yang baik
    private fun displayCategorizedArticles(categorized: CategorizedArticles) {
        Log.d(TAG, "🎯 Displaying categorized articles:")
        Log.d(TAG, "   - Headlines: ${categorized.headlines.size}")
        Log.d(TAG, "   - Edukasi: ${categorized.edukasi.size}")
        Log.d(TAG, "   - Pencegahan: ${categorized.pencegahan.size}")

        // HEADLINES
        val headlinesToShow = if (categorized.headlines.isNotEmpty()) {
            categorized.headlines.take(3)
        } else {
            Log.d(TAG, "📰 Using placeholder headlines")
            generatePlaceholderArticles(3, "Headline")
        }
        sliderAdapter.submitList(headlinesToShow)

        // ✅ PERBAIKAN: Edukasi - ambil dari headlines jika edukasi kosong
        val edukasiToShow = if (categorized.edukasi.isNotEmpty()) {
            categorized.edukasi.take(5)
        } else {
            // Coba cari edukasi dari headlines
            val edukasiFromHeadlines = categorized.headlines.filter { article ->
                val text = "${article.title} ${article.description}".lowercase()
                edukasiKeywords.any { text.contains(it, ignoreCase = true) }
            }.take(3)
            if (edukasiFromHeadlines.isNotEmpty()) {
                Log.d(TAG, "📚 Using ${edukasiFromHeadlines.size} headlines for edukasi")
                edukasiFromHeadlines
            } else {
                Log.d(TAG, "📚 Using placeholder edukasi")
                generatePlaceholderArticles(5, "Edukasi")
            }
        }
        edukasiAdapter.submitList(edukasiToShow)

        // ✅ PERBAIKAN: Pencegahan - ambil dari headlines jika pencegahan kosong
        val pencegahanToShow = if (categorized.pencegahan.isNotEmpty()) {
            categorized.pencegahan.take(5)
        } else {
            // Coba cari pencegahan dari headlines
            val pencegahanFromHeadlines = categorized.headlines.filter { article ->
                val text = "${article.title} ${article.description}".lowercase()
                pencegahanKeywords.any { text.contains(it, ignoreCase = true) }
            }.take(3)
            if (pencegahanFromHeadlines.isNotEmpty()) {
                Log.d(TAG, "🛡️ Using ${pencegahanFromHeadlines.size} headlines for pencegahan")
                pencegahanFromHeadlines
            } else {
                Log.d(TAG, "🛡️ Using placeholder pencegahan")
                generatePlaceholderArticles(5, "Pencegahan")
            }
        }
        pencegahanAdapter.submitList(pencegahanToShow)

        // Update tab mediator
        if (!firstHeadlinesShown && headlinesToShow.isNotEmpty()) {
            binding.viewPager.post { attachMediator() }
            firstHeadlinesShown = true
        }

        Log.d(TAG, "🎉 Display completed successfully!")
    }

    private fun showFallbackContent() {
        Log.w(TAG, "🔄 Showing fallback content")
        setupLoadingState(false)

        val headlines = generatePlaceholderArticles(3, "Headline")
        val edukasi = generatePlaceholderArticles(5, "Edukasi")
        val pencegahan = generatePlaceholderArticles(5, "Pencegahan")

        sliderAdapter.submitList(headlines)
        edukasiAdapter.submitList(edukasi)
        pencegahanAdapter.submitList(pencegahan)

        Toast.makeText(this, "Menampilkan konten edukasi default", Toast.LENGTH_SHORT).show()
    }

    private fun openDetail(item: ArticleItem) {
        ArticleContentActivity.start(this, item)
    }

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