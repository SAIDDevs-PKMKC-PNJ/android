package com.pkm.said

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.pkm.said.databinding.FragmentDashboardBinding
import com.pkm.said.adapter.FeatureAdapter
import com.pkm.said.adapter.NewsAdapter
import com.pkm.said.NewsActivity
import com.pkm.said.util.SessionManager

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
        val dummyNews = listOf(
            NewsItem(
                id = "1",
                title = "Mahasiswa PNJ membuat Artifi.....",
                imageUrl = null
            ),
            NewsItem(
                id = "2",
                title = "Rumah sakit terbantu dengan deteksi.....",
                imageUrl = null
            ),
            NewsItem(
                id = "3",
                title = "Teknologi AI bantu skrining dini stroke",
                imageUrl = null
            )
        )

        newsAdapter = NewsAdapter(dummyNews) { item ->
            // TODO: Arahkan ke detail berita
        }

        binding.rvNews.apply {
            adapter = newsAdapter
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
        }

        binding.btnNewsMore.setOnClickListener {
            // TODO: Arahkan ke daftar News lengkap
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}