package com.pkm.said

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Rect
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import com.bumptech.glide.Glide
import com.pkm.said.databinding.FragmentDashboardBinding
import com.pkm.said.adapter.FeatureAdapter
import com.pkm.said.adapter.NewsAdapter
import com.pkm.said.screening.RiskLevel
import com.pkm.said.screening.ScreeningActivity
import com.pkm.said.screening.ScreeningDataManager
import com.pkm.said.screening.ScreeningRepository
import com.pkm.said.screening.completedAtFormatted
import com.pkm.said.util.SessionManager
import com.pkm.said.service.NewsRetrofit
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var featureAdapter: FeatureAdapter

    private lateinit var navigationCallback: NavigationCallback

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Pastikan Activity/Context mengimplementasikan interface
        if (context is NavigationCallback) {
            navigationCallback = context
        } else {
            throw RuntimeException("$context must implement NavigationCallback")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupWelcome()
        setupSearch()
        setupScreeningCard()
        setupFeatureGrid()
        setupNewsSection()

        binding.rvFeatures.isNestedScrollingEnabled = false
        binding.rvNews.isNestedScrollingEnabled = false
    }

    private fun setupWelcome() {
        val prefsName = SessionManager.getUserName(requireContext())
        val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val finalName = prefsName ?: authUser?.displayName ?: "Pengguna"

        authUser?.let { user ->
            val docRef = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.uid)

            docRef.get()
                .addOnSuccessListener { doc ->
                    val dbPhotoUrl = doc.getString("photoUrl")
                    val finalUrl = if (!dbPhotoUrl.isNullOrBlank()) dbPhotoUrl else user.photoUrl?.toString()

                    if (!finalUrl.isNullOrBlank()) {
                        Glide.with(this)
                            .load(finalUrl)
                            .placeholder(R.drawable.ic_avatar_default)
                            .error(R.drawable.ic_avatar_default)
                            .circleCrop()
                            .into(binding.ivAvatar)
                    } else {
                        binding.ivAvatar.setImageResource(R.drawable.ic_avatar_default)
                    }
                }
                .addOnFailureListener {
                    // fallback ke Auth photoUrl
                    val authUrl = user.photoUrl?.toString()
                    if (!authUrl.isNullOrBlank()) {
                        Glide.with(this)
                            .load(authUrl)
                            .circleCrop()
                            .into(binding.ivAvatar)
                    } else {
                        binding.ivAvatar.setImageResource(R.drawable.ic_avatar_default)
                    }
                }
        } ?: run {
            // Tidak ada user login
            binding.ivAvatar.setImageResource(R.drawable.ic_avatar_default)
        }

        binding.ivAvatar.setOnClickListener {
            navigationCallback.navigateToTopLevel(R.id.navigation_profile)
        }
        binding.tvWelcome.text = getString(R.string.welcome_text, finalName)
    }

    private fun setupSearch() {
        val et = binding.etSearch
        val iv = binding.ivSearch

        val defaultString = getString(R.string.dashboard_search_bar_hint)

        fun hideKeyboard() {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(et.windowToken, 0)
        }

        fun go(raw: String) {
            val q = raw.trim()
            if (q.isEmpty()) {
                et.error = getString(R.string.dashboard_search_bar_hint)
                et.requestFocus()
                return
            }
            hideKeyboard()
            ChatbotActivity.start(requireContext(), initialMessage = q)

            et.setText(defaultString)
        }

        iv.setOnClickListener { go(et.text?.toString().orEmpty()) }

        et.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                go(et.text?.toString().orEmpty())
                true
            } else false
        }
    }

    private fun setupScreeningCard() {
        // Matikan shimmer dsb. lalu atur visibilitas
        binding.groupScoreContent.isVisible = false
        binding.groupEmptyState.isVisible = false
        binding.groupPendingState.isVisible = false

        // Jalankan fetch remote + fallback lokal
        viewLifecycleOwner.lifecycleScope.launch {
            // ✅ PERBAIKAN: Gunakan approach local-first yang sederhana
            val activeLocal = ScreeningDataManager.getCurrentSession(requireContext())
            val lastLocal = ScreeningDataManager.getLastCompletedSession(requireContext())

            // ✅ Ambil data remote sebagai backup
            val lastRemote = try {
                ScreeningRepository.getLastCompletedRemote()
            } catch (e: Exception) {
                null // Jika gagal, pakai data lokal
            }

            // ✅ Tentukan mana yang ditampilkan
            val active = activeLocal
            val last = lastRemote ?: lastLocal

            when {
                // C. Ada sesi aktif yang belum selesai
                active != null && !active.isCompleted -> {
                    val pending = ScreeningDataManager.getPendingTests(requireContext())
                    binding.groupPendingState.isVisible = true
                    binding.tvPendingSubtitle.text =
                        if (pending.isEmpty()) "Menunggu finalisasi."
                        else "Tes belum selesai: ${pending.joinToString { humanizeTestKey(it) }}"

                    // ✅ ONCLICK LISTENER TETAP SAMA
                    binding.btnContinueScreening.setOnClickListener {
                        val first = pending.firstOrNull() ?: "face_test"
                        navigateToPending(first)
                    }
                    binding.btnCancelScreening.setOnClickListener {
                        ScreeningDataManager.cancelSession(requireContext())
                        setupScreeningCard() // refresh UI
                    }
                }

                // A. Ada hasil final terakhir
                last != null -> {
                    binding.groupScoreContent.isVisible = true
                    val percent = ScreeningDataManager.calculateBEFASTOverallPercent(last)
                    binding.tvScoreValue.text = "$percent%"
                    binding.tvScoreStatus.text = ScreeningDataManager.getRiskLabel(last)
                    applyRiskColor(last.overallRisk)

                    val whenStr = last.completedAtFormatted().let { if (it == "-") last.timestamp else it }
                    binding.tvScoreTimestamp.text = whenStr
                    binding.tvScoreTimestamp.isVisible = true
                }

                // B. Tidak ada riwayat sama sekali
                else -> {
                    binding.groupEmptyState.isVisible = true
                    // ✅ ONCLICK LISTENER TETAP SAMA
                    binding.btnStartScreening.setOnClickListener {
                        ScreeningActivity.start(requireContext(), userId = null)
                    }
                }
            }

            // ✅ ONCLICK LISTENER TETAP SAMA
            binding.chipRestart.setOnClickListener {
                ScreeningActivity.start(requireContext(), userId = null)
            }
            binding.tvHistoryLink.setOnClickListener {
                navigationCallback.navigateToTopLevel(R.id.navigation_history)
            }
        }
    }


    // Helper: sama seperti di fragment result-mu
    private fun humanizeTestKey(key: String): String = when (key.lowercase()) {
        "face_test","face" -> "Face"
        "arms_test","arms","arm_test","arm","befast_arm" -> "Arms"
        "speech_test","speech","befast_speech" -> "Speech"
        else -> key
    }

    // Arahkan ke fragment pending yang benar
    private fun navigateToPending(first: String) {
        val intent = Intent(requireContext(), ScreeningActivity::class.java).apply {
            // kirim tujuan fragment
            putExtra("dest", when (first) {
                "face_test","face" -> "face"
                "arms_test","arms" -> "arms"
                "speech_test","speech" -> "speech"
                else -> "face"
            })
        }
        startActivity(intent)
    }

    private fun applyRiskColor(risk: RiskLevel) {
        val colorRes = when (risk) {
            RiskLevel.LOW -> R.color.success_color
            RiskLevel.MEDIUM -> R.color.risk_medium_text
            RiskLevel.HIGH -> R.color.risk_high_text
            RiskLevel.CRITICAL -> R.color.risk_high_text
            RiskLevel.UNKNOWN -> R.color.risk_unknown_text
            else -> R.color.risk_unknown_text
        }

        binding.tvScoreValue.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
        binding.tvScoreStatus.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    private fun setupFeatureGrid() {
        val featureItems = listOf(
            FeatureItem(
                id = "chatbot",
                iconRes = R.drawable.ic_chatbot,
                title = getString(R.string.feature_chatbot)
            ),
            FeatureItem(
                id = "darurat",
                iconRes = R.drawable.ic_sos,
                title = getString(R.string.feature_darurat)
            ),
            FeatureItem(
                id = "skrining",
                iconRes = R.drawable.ic_screening,
                title = getString(R.string.feature_screening)
            ),
            FeatureItem(
                id = "berita",
                iconRes = R.drawable.ic_news,
                title = getString(R.string.feature_edukasi)
            ),
        )

        featureAdapter = FeatureAdapter(featureItems) { item ->
            when (item.id) {
                "chatbot" -> {
                    startActivity(Intent(requireContext(), ChatbotActivity::class.java))
                }
                "darurat" -> {
                    startActivity(Intent(requireContext(), EmergencyActivity::class.java))
                }
                "skrining" -> {
                    startActivity(Intent(requireContext(), ScreeningManagementActivity::class.java))
                }
                "berita" -> {
                    startActivity(Intent(requireContext(), NewsActivity::class.java))
                }
            }
        }
        binding.rvFeatures.apply {
            adapter = featureAdapter
            layoutManager = GridLayoutManager(requireContext(), 4)
            setHasFixedSize(true)

            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: Rect,
                    view: View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    val space = resources.getDimensionPixelSize(R.dimen.spacing_4) // atau 16dp
                    val position = parent.getChildAdapterPosition(view)

                    // Beri jarak kanan untuk semua item kecuali yang terakhir
                    if (position % 4 != 3) {
                        outRect.right = space
                    }

                    // Optional: tambahkan jarak bawah untuk baris
                    outRect.bottom = space
                }
            })

        }
    }

    private fun setupNewsSection() {
        val apiKey = BuildConfig.NEWS_API_KEY
        val q = "stroke (health OR symptoms OR prevention) -\"heat stroke\" -\"The Strokes\""

        val searchIn = "title,description"
        val deviceLang = Locale.getDefault().language
        val apiLang = if (deviceLang == "id") "id" else "en"

        Log.d("Dashboard", "🔍 Query: '$q'")
        Log.d("Dashboard", "🔍 Language: '$apiLang'")

        // Panggil API top-headlines untuk Indonesia
        NewsRetrofit.api.searchEverything(
            q = q,
            language = apiLang,
            searchIn = searchIn,
            sortBy = "publishedAt",
            page = 1,
            pageSize = 30,
            apiKey = apiKey
        )
            .enqueue(object : Callback<NewsResponse> {
                override fun onResponse(call: Call<NewsResponse>, response: Response<NewsResponse>) {
                    if (!response.isSuccessful) {
                        Toast.makeText(requireContext(), "Gagal: ${response.code()}", Toast.LENGTH_SHORT).show()
                        showFallbackNews()
                        return
                    }

                    // Map ke ArticleItem
                    val articles = response.body()?.articles ?: emptyList()
                    Log.d("Dashboard", "📰 Raw articles received: ${articles.size}")

                    // ✅ DEBUG: Tampilkan semua judul artikel
                    articles.forEachIndexed { index, article ->
                        Log.d("Dashboard", "📄 Article $index: ${article.title ?: "No Title"}")
                        Log.d("Dashboard", "   Source: ${article.source?.name ?: "Unknown"}")
                    }

                    if (articles.isEmpty()) {
                        Log.w("Dashboard", "⚠️ No articles in response")
                        showFallbackNews()
                        return
                    }

                    // ✅ GUNAKAN EXACT SAME LOGIC dengan NewsActivity
                    val relevantArticles = articles.getRelevantStrokeArticles()
                    Log.d("Dashboard", "🔧 Relevant articles after filtering: ${relevantArticles.size}")

                    // ✅ DEBUG: Tampilkan artikel yang lolos filter
                    relevantArticles.forEachIndexed { index, article ->
                        Log.d("Dashboard", "✅ Relevant $index: ${article.title ?: "No Title"}")
                    }

                    if (relevantArticles.isEmpty()) {
                        Log.w("Dashboard", "⚠️ No relevant articles after filtering")
                        showFallbackNews()
                        return
                    }

                    val articleItems = relevantArticles.map { it.toCommonArticleItem() }
                    val headlines = articleItems.take(5)

                    Log.d("Dashboard", "🎯 Final headlines for dashboard: ${headlines.size}")


                    // Adapter pakai ArticleItem
                    val adapter = NewsAdapter(
                        data = headlines,
                        onClick = { article -> openDetail(article) }
                    )

                    // Pasang ke RecyclerView (horizontal slider)
                    binding.rvNews.apply {
                        layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                        this.adapter = adapter

                        // Snap 1 kartu per-scroll
                        if (onFlingListener == null) {
                            PagerSnapHelper().attachToRecyclerView(this)
                        }

                        // Spasi antar item (pasang sekali)
                        if (itemDecorationCount == 0) {
                            addItemDecoration(object : RecyclerView.ItemDecoration() {
                                override fun getItemOffsets(
                                    outRect: Rect,
                                    view: View,
                                    parent: RecyclerView,
                                    state: RecyclerView.State
                                ) {
                                    val space = resources.getDimensionPixelSize(R.dimen.spacing_12)
                                    val pos = parent.getChildAdapterPosition(view)
                                    outRect.right = space
                                    if (pos == 0) outRect.left = space
                                }
                            })
                        }
                    }
                    binding.rvNews.visibility = View.VISIBLE
                }
                override fun onFailure(call: Call<NewsResponse>, t: Throwable) {
                    Toast.makeText(requireContext(), "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })

        binding.btnNewsMore.setOnClickListener {
            val intent = Intent(requireContext(), NewsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showFallbackNews() {
        val fallbackArticles = generatePlaceholderArticles(3, "Headline")

        val adapter = NewsAdapter(
            data = fallbackArticles,
            onClick = { article -> openDetail(article) }
        )

        binding.rvNews.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            this.adapter = adapter

            if (onFlingListener == null) {
                PagerSnapHelper().attachToRecyclerView(this)
            }

            if (itemDecorationCount == 0) {
                addItemDecoration(object : RecyclerView.ItemDecoration() {
                    override fun getItemOffsets(
                        outRect: Rect,
                        view: View,
                        parent: RecyclerView,
                        state: RecyclerView.State
                    ) {
                        val space = resources.getDimensionPixelSize(R.dimen.spacing_12)
                        val pos = parent.getChildAdapterPosition(view)
                        outRect.right = space
                        if (pos == 0) outRect.left = space
                    }
                })
            }
        }
        binding.rvNews.visibility = View.VISIBLE
    }

    private fun openDetail(item: ArticleItem) {
        // Pakai helper bawaan activity biar selalu kirim JSON + fallback
        ArticleContentActivity.start(requireContext(), item)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}