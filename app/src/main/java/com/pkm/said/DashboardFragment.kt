package com.pkm.said

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Rect
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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
import com.pkm.said.util.SessionManager
import com.pkm.said.service.NewsRetrofit
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var featureAdapter: FeatureAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
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
            findNavController().navigate(R.id.action_dashboard_to_profile)
        }
        binding.tvWelcome.text = getString(R.string.welcome_text, finalName)
    }

    private fun setupSearch() {
        binding.ivSearch.setOnClickListener {
            startActivity(Intent(requireContext(), ChatbotActivity::class.java))
        }
    }

    private fun setupScreeningCard() {
        // Matikan shimmer dsb. lalu atur visibilitas
        binding.groupScoreContent.isVisible = false
        binding.groupEmptyState.isVisible = false
        binding.groupPendingState.isVisible = false

        val active = ScreeningDataManager.getCurrentSession(requireContext())
        val last   = ScreeningDataManager.getLastCompletedSession(requireContext())

        when {
            // C. Ada sesi aktif yang belum selesai
            active != null && !active.isCompleted -> {
                val pending = ScreeningDataManager.getPendingTests(requireContext())
                binding.groupPendingState.isVisible = true
                binding.tvPendingSubtitle.text =
                    if (pending.isEmpty()) "Menunggu finalisasi."
                    else "Tes belum selesai: ${pending.joinToString { humanizeTestKey(it) }}"

                binding.btnContinueScreening.setOnClickListener {
                    // Arahkan ke tes pertama yang pending (fallback ke Face)
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
                val percent = ScreeningDataManager.calculateFASTOverallPercent(last)
                binding.tvScoreValue.text = "$percent%"
                binding.tvScoreStatus.text = ScreeningDataManager.getRiskLabel(last)
                applyRiskColor(last.overallRisk)

                binding.tvScoreTimestamp.text = last.completedAt ?: last.timestamp
                binding.tvScoreTimestamp.isVisible = true
            }

            // B. Tidak ada riwayat sama sekali
            else -> {
                binding.groupEmptyState.isVisible = true
                binding.btnStartScreening.setOnClickListener {
                    // Mulai flow screening
                    ScreeningActivity.start(requireContext(), userId = null)
                }
            }
        }

        // Aksi tambahan (opsional)
        binding.chipRestart.setOnClickListener {
            ScreeningActivity.start(requireContext(), userId = null)
        }
        binding.tvHistoryLink.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_history)
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
                id = "edukasi",
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
                    // TODO: Arahkan ke halaman Darurat
                }
                "skrining" -> {
                    startActivity(Intent(requireContext(), HomeActivity::class.java))
                }
                "edukasi" -> {
                    startActivity(Intent(requireContext(), NewsActivity::class.java))
                }
            }
        }
        binding.rvFeatures.apply {
            adapter = featureAdapter
            layoutManager = GridLayoutManager(requireContext(), 4)
            setHasFixedSize(true)
        }
    }

    private fun setupNewsSection() {
        val apiKey = BuildConfig.NEWS_API_KEY
        val q = "kesehatan AND (perawatan OR pencegahan OR prevention OR treatment)"

        // Panggil API top-headlines untuk Indonesia
        NewsRetrofit.api.searchEverything(
            q = q,
            language = "id",
            sortBy = "publishedAt",
            page = 1,
            pageSize = 20,
            apiKey = apiKey
        )
            .enqueue(object : Callback<NewsResponse> {
                override fun onResponse(call: Call<NewsResponse>, response: Response<NewsResponse>) {
                    if (!response.isSuccessful) {
                        Toast.makeText(requireContext(), "Gagal: ${response.code()}", Toast.LENGTH_SHORT).show()
                        return
                    }

                    // Map ke ArticleItem
                    val articles: List<ArticleItem> =
                        response.body()?.articles.orEmpty().map { it.toArticleItem() }

                    // Adapter pakai ArticleItem
                    val adapter = NewsAdapter(
                        data = articles,
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

    private fun openDetail(item: ArticleItem) {
        val intent = Intent(requireContext(), ArticleContentActivity::class.java).apply {
            putExtra("article", item) // ArticleItem sudah @Parcelize
        }
        startActivity(intent)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}