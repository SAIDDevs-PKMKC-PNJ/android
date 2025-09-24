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
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import com.pkm.said.databinding.FragmentDashboardBinding
import com.pkm.said.adapter.FeatureAdapter
import com.pkm.said.adapter.NewsAdapter
import com.pkm.said.NewsActivity
import com.pkm.said.util.SessionManager
import com.pkm.said.BuildConfig
import com.pkm.said.service.NewsRetrofit
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var newsAdapter: NewsAdapter
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

        binding.tvWelcome.text = getString(R.string.welcome_text, finalName)
    }

    private fun setupSearch() {
        binding.ivSearch.setOnClickListener {
            val query = binding.etSearch.text?.toString().orEmpty()
            if (query.isNotBlank()) {
                // TODO arahkan ke fitur edukasi / pencarian artikel
            }
        }
    }

    private fun setupScreeningCard() {
        // Dummy data score
        val score = 30
        binding.tvScoreValue.text = "$score%"
        binding.tvScoreStatus.text = getString(R.string.home_score_status_normal)

        binding.chipRestart.setOnClickListener {
            val intent = Intent(requireContext(), HomeActivity::class.java)
            startActivity(intent)
        }

        // Contoh show/hide placeholder saat loading
        binding.groupScoreContent.isVisible = true
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