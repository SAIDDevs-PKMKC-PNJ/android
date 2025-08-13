package com.pkm.said

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.pkm.said.databinding.FragmentNewsBinding

class NewsFragment : Fragment() {

    companion object {
        private const val TAG = "NewsFragment"
    }

    // View Binding
    private var _binding: FragmentNewsBinding? = null
    private val binding get() = _binding!!

    // Adapters
    private lateinit var edukasiAdapter: ArticleAdapter
    private lateinit var pencegahanAdapter: ArticleAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "=== NEWS FRAGMENT DEBUG ===")
        Log.d(TAG, "Current Date: 2025-07-30 16:21:18")
        Log.d(TAG, "Current User: itsLuxra")
        Log.d(TAG, "onCreateView called")

        return try {
            _binding = FragmentNewsBinding.inflate(inflater, container, false)
            Log.d(TAG, "✅ View Binding setup completed")
            binding.root
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up View Binding", e)
            throw e
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        try {
            setupRecyclerViews()
            loadData()
            Log.d(TAG, "✅ NewsFragment setup completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onViewCreated", e)
            showError("Terjadi kesalahan saat memuat data")
        }
    }

    // ✅ VIEW BINDING - Cleaner RecyclerView setup
    private fun setupRecyclerViews() {
        Log.d(TAG, "Setting up RecyclerViews...")

        try {
            // Setup Edukasi RecyclerView
            binding.rvEdukasiStroke.apply {
                layoutManager = LinearLayoutManager(
                    requireContext(),
                    LinearLayoutManager.HORIZONTAL,
                    false
                )
                setHasFixedSize(true)
            }

            // Setup Pencegahan RecyclerView
            binding.rvPencegahanStroke.apply {
                layoutManager = LinearLayoutManager(
                    requireContext(),
                    LinearLayoutManager.HORIZONTAL,
                    false
                )
                setHasFixedSize(true)
            }

            Log.d(TAG, "✅ RecyclerViews setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up RecyclerViews", e)
            throw e
        }
    }

    // ✅ IMPROVED - Better data loading with error handling
    private fun loadData() {
        Log.d(TAG, "Loading data...")

        try {
            // Create data
            val edukasiData = createEdukasiArticles()
            val pencegahanData = createPencegahanArticles()

            // Validate data
            if (edukasiData.isEmpty()) {
                Log.w(TAG, "⚠️ Edukasi data is empty")
            }
            if (pencegahanData.isEmpty()) {
                Log.w(TAG, "⚠️ Pencegahan data is empty")
            }

            // Setup adapters with click handling
            edukasiAdapter = ArticleAdapter(edukasiData) { article ->
                handleArticleClick(article)
            }

            pencegahanAdapter = ArticleAdapter(pencegahanData) { article ->
                handleArticleClick(article)
            }

            // Set adapters using binding
            binding.rvEdukasiStroke.adapter = edukasiAdapter
            binding.rvPencegahanStroke.adapter = pencegahanAdapter

            Log.d(TAG, "✅ Data loaded - Edukasi: ${edukasiData.size}, Pencegahan: ${pencegahanData.size}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading data", e)
            showError("Gagal memuat artikel")
        }
    }

    // ✅ ENHANCED - More realistic and comprehensive articles
    private fun createEdukasiArticles(): List<Article> {
        return listOf(
            Article(
                id = 1,
                title = "Apa Itu Stroke? Pengenalan Dasar untuk Pemula",
                date = "2 jam lalu",
                source = "Dr. Sarah Medika, Sp.N",
                imageUrl = "https://example.com/stroke-basic.jpg",
                category = "Edukasi",
                description = "Pelajari definisi stroke, penyebab utama, dan mengapa kondisi ini perlu mendapat perhatian serius."
            ),
            Article(
                id = 2,
                title = "Jenis-Jenis Stroke: Iskemik vs Hemoragik",
                date = "4 jam lalu",
                source = "Prof. Dr. Neurologi RSUD",
                imageUrl = "https://example.com/stroke-types.jpg",
                category = "Edukasi",
                description = "Memahami perbedaan antara stroke iskemik dan hemoragik serta karakteristik masing-masing."
            ),
            Article(
                id = 3,
                title = "Gejala Stroke: Kenali Tanda FAST",
                date = "6 jam lalu",
                source = "Tim Medis Emergency",
                imageUrl = "https://example.com/stroke-symptoms.jpg",
                category = "Edukasi",
                description = "Metode FAST (Face, Arms, Speech, Time) untuk mengenali gejala stroke secara dini."
            ),
            Article(
                id = 4,
                title = "Dampak Jangka Panjang Stroke pada Pasien",
                date = "8 jam lalu",
                source = "Dr. Rehabilitation Center",
                imageUrl = "https://example.com/stroke-impact.jpg",
                category = "Edukasi",
                description = "Bagaimana stroke mempengaruhi kehidupan sehari-hari dan proses pemulihan yang diperlukan."
            ),
            Article(
                id = 5,
                title = "Teknologi Terbaru dalam Diagnosis Stroke",
                date = "1 hari lalu",
                source = "Research Team Medical",
                imageUrl = "https://example.com/stroke-tech.jpg",
                category = "Edukasi",
                description = "Perkembangan teknologi CT scan dan MRI untuk diagnosis stroke yang lebih akurat."
            )
        )
    }

    // ✅ ENHANCED - More comprehensive prevention articles
    private fun createPencegahanArticles(): List<Article> {
        return listOf(
            Article(
                id = 6,
                title = "7 Langkah Pencegahan Stroke di Usia Muda",
                date = "1 jam lalu",
                source = "Dr. Preventif Medicine",
                imageUrl = "https://example.com/prevention-young.jpg",
                category = "Pencegahan",
                description = "Tips praktis mencegah stroke sejak dini dengan gaya hidup sehat."
            ),
            Article(
                id = 7,
                title = "Diet Mediterranean: Kunci Anti Stroke",
                date = "3 jam lalu",
                source = "Ahli Gizi Klinik",
                imageUrl = "https://example.com/diet-stroke.jpg",
                category = "Pencegahan",
                description = "Pola makan Mediterranean terbukti mengurangi risiko stroke hingga 30%."
            ),
            Article(
                id = 8,
                title = "Olahraga 30 Menit: Investasi Anti Stroke",
                date = "5 jam lalu",
                source = "Fisioterapis Bersertifikat",
                imageUrl = "https://example.com/exercise-stroke.jpg",
                category = "Pencegahan",
                description = "Jenis olahraga yang efektif untuk menjaga kesehatan pembuluh darah."
            ),
            Article(
                id = 9,
                title = "Manajemen Stress untuk Mencegah Stroke",
                date = "7 jam lalu",
                source = "Psikolog Medis",
                imageUrl = "https://example.com/stress-management.jpg",
                category = "Pencegahan",
                description = "Teknik mengelola stress dan dampaknya terhadap risiko stroke."
            ),
            Article(
                id = 10,
                title = "Kontrol Tekanan Darah: Kunci Utama",
                date = "9 jam lalu",
                source = "Dokter Jantung",
                imageUrl = "https://example.com/blood-pressure.jpg",
                category = "Pencegahan",
                description = "Bagaimana menjaga tekanan darah normal untuk mencegah stroke."
            )
        )
    }

    // ✅ IMPROVED - Better click handling with navigation
    private fun handleArticleClick(article: Article) {
        Log.d(TAG, "Article clicked: ${article.title} (ID: ${article.id})")

        try {
            // Show immediate feedback
            showToast("Membuka: ${article.title}")

            // TODO - Navigate to article detail when route is ready
            // Example navigation (uncomment when ArticleDetailFragment is ready):
            /*
            val action = NewsFragmentDirections.actionNewsToArticleDetail(
                articleId = article.id,
                articleTitle = article.title
            )
            findNavController().navigate(action)
            */

            // Temporary logging for development
            Log.d(TAG, "📰 Article Details:")
            Log.d(TAG, "   Title: ${article.title}")
            Log.d(TAG, "   Source: ${article.source}")
            Log.d(TAG, "   Category: ${article.category}")
            Log.d(TAG, "   Description: ${article.description}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling article click", e)
            showError("Gagal membuka artikel")
        }
    }

    // ✅ UTILITY FUNCTIONS
    private fun showToast(message: String) {
        try {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing toast", e)
        }
    }

    private fun showError(message: String) {
        try {
            Toast.makeText(requireContext(), "Error: $message", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Error shown to user: $message")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing error message", e)
        }
    }

    // ✅ VIEW BINDING - Proper cleanup
    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView called")

        try {
            // Clean up binding reference
            _binding = null
            Log.d(TAG, "✅ NewsFragment cleaned up, binding nullified")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in cleanup", e)
        }
    }
}

// ✅ ENHANCED Article data class
data class Article(
    val id: Int,
    val title: String,
    val date: String,
    val source: String,
    val imageUrl: String,
    val category: String,
    val description: String = "" // Added description field
)