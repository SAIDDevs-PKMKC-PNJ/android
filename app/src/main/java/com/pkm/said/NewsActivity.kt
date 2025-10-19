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
import com.google.firebase.auth.FirebaseAuth
import com.pkm.said.adapter.ArticleListAdapter
import com.pkm.said.adapter.NewsSliderAdapter
import com.pkm.said.databinding.ActivityNewsBinding
import com.pkm.said.screening.ScreeningActivity
import com.pkm.said.service.NewsRetrofit
import com.pkm.said.util.SessionManager
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class NewsActivity : AppCompatActivity(), Said.VoiceActivityCallback {

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

        Said.getInstance().registerActivityCallback(this.localClassName, this)
        setupToolbar()
        setupSlider()
        setupLists()
        setupLoadingState(true)
        loadDataFromApi()
    }

    private fun handleBackNavigation() {
        try {
            Log.d(TAG, "🔄 Handling back navigation in News...")
            navigateToDashboard()
            Log.d(TAG, "✅ Back navigation completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in back navigation", e)
            finish() // Fallback
        }
    }

    // ✅ UPDATE TOOLBAR SETUP
    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            handleBackNavigation()
        }
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

    override fun onVoiceCommand(command: String, extras: Bundle?): Boolean {
        Log.d(TAG, "🎤 Voice command received in News: $command")
        return when (command.toLowerCase()) {
            // SCREENING - sama seperti MainActivity
            "tes stroke", "mulai screening", "screening", "mulai tes" -> {
                startStrokeScreening()
                true
            }
            // EMERGENCY - sama seperti MainActivity
            "darurat", "emergency", "tolong" -> {
                handleEmergencyFromVoice()
                true
            }
            // DASHBOARD - kembali ke MainActivity
            "dashboard", "home", "kembali" -> {
                navigateToDashboard()
                true
            }
            else -> false
        }
    }

    // ✅ NAVIGATION - UPDATE UNTUK SCREENING & EMERGENCY
    override fun onNavigateTo(destination: String): Boolean {
        Log.d(TAG, "🧭 Navigation command in News: $destination")
        return when (destination.toLowerCase()) {
            "dashboard", "home" -> {
                navigateToDashboard()
                true
            }
            "screening" -> {
                startStrokeScreening()
                true
            }
            "emergency" -> {
                handleEmergencyFromVoice()
                true
            }
            else -> false
        }
    }

    // ✅ SUPPORTED COMMANDS - UPDATE DENGAN SCREENING & EMERGENCY
    override fun getSupportedCommands(): List<String> {
        return listOf(
            "tes stroke", "mulai screening", "screening", "mulai tes",
            "darurat", "emergency", "tolong",
            "dashboard", "home", "kembali"
        )
    }

    // ✅ STROKE SCREENING - SAMA SEPERTI DI MAINACTIVITY
    private fun startStrokeScreening() {
        try {
            Log.d(TAG, "🏥 Starting stroke screening from News...")

            // Dapatkan username seperti di MainActivity
            val username = getCurrentUsername()
            Log.d(TAG, "Username: $username")

            // ✅ GUNAKAN METHOD start() DARI SCREENINGACTIVITY - sama seperti MainActivity
            ScreeningActivity.start(this, username, startNew = true)

            if (!isFinishing && !isDestroyed) {
                Toast.makeText(
                    this,
                    "🏥 Starting screening for $username",
                    Toast.LENGTH_SHORT
                ).show()
            }
            Log.d(TAG, "✅ ScreeningActivity started successfully from News")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting stroke screening from News", e)
            if (!isFinishing && !isDestroyed) {
                Toast.makeText(this, "❌ Failed to start screening", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ✅ EMERGENCY HANDLER - SAMA SEPERTI DI MAINACTIVITY
    private fun handleEmergencyFromVoice() {
        try {
            Log.d(TAG, "🚨 Emergency from voice command in News - starting EmergencyActivity")
            val intent = Intent(this, EmergencyActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("from_voice_command", true)
                putExtra("from_news", true) // Tambahkan identifier
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start EmergencyActivity from News, using fallback", e)
            // Fallback ke dialog emergency
            showEmergencyFallbackDialog()
        }
    }

    // ✅ EMERGENCY FALLBACK DIALOG - SAMA SEPERTI DI MAINACTIVITY
    private fun showEmergencyFallbackDialog() {
        Log.d(TAG, "🚨 Emergency fallback in News - showing dialog")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🚨 Emergency Detected")
            .setMessage("Voice assistant detected emergency situation. Please manually open emergency features.")
            .setPositiveButton("Open Emergency") { _, _ ->
                // Try to start EmergencyActivity again dengan approach berbeda
                try {
                    val intent = Intent(this, EmergencyActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("from_news", true)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot open emergency screen", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "❌ Emergency fallback also failed in News", e)
                }
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }

    // ✅ GET CURRENT USERNAME - SAMA SEPERTI DI MAINACTIVITY
    private fun getCurrentUsername(): String {
        return try {
            SessionManager.getUserName(this) ?: getFallbackUsername()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting username in News", e)
            getFallbackUsername()
        }
    }

    private fun getFallbackUsername(): String {
        return try {
            // Coba dapatkan dari Firebase Auth sebagai fallback
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            when {
                firebaseUser?.displayName != null -> {
                    val username = firebaseUser.displayName!!
                    // Simpan ke SessionManager untuk konsistensi
                    saveUsernameToSessionManager(username)
                    username
                }

                firebaseUser?.email != null -> {
                    val email = firebaseUser.email!!
                    val usernameFromEmail = email.substringBefore("@")
                    saveUsernameToSessionManager(usernameFromEmail)
                    usernameFromEmail
                }

                else -> generateAnonymousUsername()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting fallback username", e)
            generateAnonymousUsername()
        }
    }

    private fun saveUsernameToSessionManager(username: String) {
        try {
            // Jika user sudah login di Firebase, update SessionManager
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            firebaseUser?.let { user ->
                SessionManager.saveBasicFromFirebase(this, user, "auto_detected")
            }
            Log.d(TAG, "✅ Username saved to SessionManager: $username")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving username to SessionManager", e)
        }
    }

    private fun generateAnonymousUsername(): String {
        val anonymousUser = "user_${System.currentTimeMillis()}"
        Log.d(TAG, "Generated anonymous username in News: $anonymousUser")
        return anonymousUser
    }

    private fun navigateToDashboard() {
        try {
            Log.d(TAG, "🚀 Navigating to Dashboard - finishing NewsActivity")

            // Cukup finish() karena MainActivity sudah default ke dashboard
            finish()

            // Optional: smooth transition animation
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

            Log.d(TAG, "✅ Navigation to dashboard completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error navigating to dashboard", e)
            // Fallback - tetap coba finish
            finish()
        }
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
        Said.getInstance().unregisterActivityCallback(this.localClassName)
    }
}