package com.pkm.said

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayoutMediator
import com.pkm.said.ArticleItem
import com.pkm.said.R
import com.pkm.said.adapter.ArticleListAdapter
import com.pkm.said.adapter.NewsSliderAdapter
import com.pkm.said.databinding.ActivityNewsBinding

class NewsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "NewsActivity"
        private const val AUTO_SCROLL_INTERVAL = 4000L
    }

    private lateinit var binding: ActivityNewsBinding

    private lateinit var sliderAdapter: NewsSliderAdapter
    private lateinit var edukasiAdapter: ArticleListAdapter
    private lateinit var pencegahanAdapter: ArticleListAdapter

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

        Log.d(TAG, "=== NEWS ACTIVITY DEBUG ===")
        Log.d(TAG, "onCreate")

        setupToolbar()
        setupSlider()
        setupLists()
        loadData()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.tvTitle.text = getString(R.string.news_title_stroke_world) // "Berita Dunia Stroke"
    }

    private fun setupSlider() {
        sliderAdapter = NewsSliderAdapter(onClick = { article ->
            showToast("Buka headline: ${article.title}")
            // TODO: Navigate to detail article
        })
        binding.viewPager.adapter = sliderAdapter

        // Page transformer untuk efek scale dan margin
        val pageMargin = resources.getDimensionPixelSize(R.dimen.slider_page_margin)
        val pageOffset = resources.getDimensionPixelSize(R.dimen.slider_page_offset)
        binding.viewPager.setPageTransformer { page, position ->
            page.translationX = -pageOffset * position
            val scale = 0.92f + (1 - kotlin.math.abs(position)) * 0.08f
            page.scaleY = scale
        }

        TabLayoutMediator(binding.tabDots, binding.viewPager) { _, _ -> }.attach()
    }

    private fun setupLists() {
        edukasiAdapter = ArticleListAdapter { article ->
            showToast("Buka Edukasi: ${article.title}")
            // TODO navigate to detail
        }
        pencegahanAdapter = ArticleListAdapter { article ->
            showToast("Buka Pencegahan: ${article.title}")
            // TODO navigate to detail
        }

        binding.rvEdukasi.apply {
            layoutManager = LinearLayoutManager(this@NewsActivity, LinearLayoutManager.VERTICAL, false)
            adapter = edukasiAdapter
            setHasFixedSize(true)
        }

        binding.rvPencegahan.apply {
            layoutManager = LinearLayoutManager(this@NewsActivity, LinearLayoutManager.VERTICAL, false)
            adapter = pencegahanAdapter
            setHasFixedSize(true)
        }
    }

    private fun loadData() {
        // Kamu bisa ganti dengan sumber data asli (API/Firestore)
        val edukasi = createEdukasiArticles()
        val pencegahan = createPencegahanArticles()

        // Headline slider: gunakan kombinasi atau list khusus headline
        val headlines = listOf(
            edukasi.firstOrNull() ?: sampleArticle(1),
            pencegahan.firstOrNull() ?: sampleArticle(2),
            sampleArticle(3).copy(title = "Aplikasi Skrining Stroke Gratis di PNJ")
        )

        sliderAdapter.submitList(headlines)
        edukasiAdapter.submitList(edukasi)
        pencegahanAdapter.submitList(pencegahan)
    }

    // Dummy data (pakai milikmu jika sudah ada)
    private fun createEdukasiArticles(): List<ArticleItem> {
        return listOf(
            ArticleItem(1, "Apa Itu Stroke? Pengenalan Dasar untuk Pemula", "2 jam lalu", "Dr. Sarah Medika, Sp.N",
                "https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?q=80&w=1600&auto=format&fit=crop", "Edukasi",
                "Pelajari definisi stroke, penyebab utama, dan kenapa ini perlu perhatian.",
                """
                    Stroke adalah kondisi darurat medis ketika aliran darah ke bagian otak terhenti, sehingga sel-sel otak kekurangan oksigen dan nutrisi. Tanpa penanganan cepat, sel-sel tersebut dapat mati dan menyebabkan kelumpuhan, gangguan bicara, hingga penurunan fungsi kognitif.
                    Penyebab utama stroke antara lain hipertensi, kolesterol tinggi, diabetes, merokok, dan gaya hidup sedentari. Mengenali faktor risiko dan melakukan pencegahan sejak dini bisa menurunkan risiko secara signifikan.
                    Jika menemukan gejala, segera cari pertolongan medis. Waktu adalah otak — semakin cepat ditangani, semakin baik hasilnya.
                """.trimIndent()),
            ArticleItem(2, "Jenis-Jenis Stroke: Iskemik vs Hemoragik", "4 jam lalu", "Prof. Dr. Neurologi RSUD",
                "https://images.unsplash.com/photo-1519491092129-1f51b0b6c3c7?q=80&w=1600&auto=format&fit=crop", "Edukasi",
                "Perbedaan stroke iskemik dan hemoragik.",
                """
                    Stroke iskemik terjadi saat pembuluh darah di otak tersumbat, biasanya oleh bekuan darah. Ini merupakan tipe yang paling umum. Sementara stroke hemoragik terjadi saat pembuluh darah pecah dan menyebabkan perdarahan di otak.
                    Penanganan keduanya berbeda: stroke iskemik sering memerlukan obat penghancur bekuan (trombolisis) atau tindakan mekanik, sedangkan hemoragik berfokus pada kontrol perdarahan dan tekanan intrakranial. Diagnosis cepat lewat CT scan sangat krusial.
                """.trimIndent()),
            ArticleItem(3, "Gejala Stroke: Kenali Tanda FAST", "6 jam lalu", "Tim Medis Emergency",
                "https://images.unsplash.com/photo-1504439468489-c8920d796a29?q=80&w=1600&auto=format&fit=crop", "Edukasi",
                "Metode FAST untuk deteksi dini.",
                """
                FAST: Face drooping, Arm weakness, Speech difficulty, Time to call. Perhatikan senyum yang tidak simetris, tangan sebelah melemah, dan bicara pelo atau sulit memahami. 
                Jika salah satu gejala muncul mendadak, segera hubungi layanan darurat. Jangan menunggu gejala menghilang sendiri. Setiap menit berharga untuk menyelamatkan fungsi otak.
                """.trimIndent()),
        )
    }

    private fun createPencegahanArticles(): List<ArticleItem> {
        return listOf(
            ArticleItem(6, "7 Langkah Pencegahan Stroke di Usia Muda", "1 jam lalu", "Dr. Preventif Medicine",
                "https://images.unsplash.com/photo-1518310383802-640c2de311b2?q=80&w=1600&auto=format&fit=crop", "Pencegahan",
                "Tips praktis mencegah stroke sejak dini.",
                """
                Mencegah stroke di usia muda dimulai dari kebiasaan harian: jaga tekanan darah, batasi garam, berhenti merokok, kelola stres, tidur cukup, rutin olahraga, dan cek kesehatan berkala. 
                Mulailah dari langkah kecil yang konsisten. Perubahan 1% tiap hari akan terasa besar dalam hitungan bulan.
                """.trimIndent()),
            ArticleItem(7, "Diet Mediterranean: Kunci Anti Stroke", "3 jam lalu", "Ahli Gizi Klinik",
                "https://images.unsplash.com/photo-1526318472351-c75fcf070305?q=80&w=1600&auto=format&fit=crop", "Pencegahan",
                "Pola makan Mediterranean mengurangi risiko stroke.",
                """
                    Pola makan Mediterranean kaya sayur, buah, kacang-kacangan, ikan, minyak zaitun, dan gandum utuh terbukti menurunkan risiko penyakit kardiovaskular dan stroke. 
                    Fokus pada makanan minim proses, kurangi gula tambahan, serta pilih lemak sehat. Rasa enak, kenyang lebih lama, dan kesehatan yang lebih baik.
                    """.trimIndent()),
            ArticleItem(8, "Olahraga 30 Menit: Investasi Anti Stroke", "5 jam lalu", "Fisioterapis",
                "https://images.unsplash.com/photo-1517649763962-0c623066013b?q=80&w=1600&auto=format&fit=crop", "Pencegahan",
                "Jenis olahraga yang efektif.",
                """
                Olahraga aerobik intensitas sedang 30 menit sehari seperti jalan cepat, bersepeda, atau berenang membantu menurunkan tekanan darah, memperbaiki profil lipid, dan mengurangi peradangan — faktor penting pencegahan stroke. 
                Kombinasikan dengan latihan kekuatan 2–3 kali seminggu untuk hasil optimal.
                """.trimIndent()),
        )
    }

    private fun sampleArticle(id: Int) = ArticleItem(
        id = id,
        title = "Mahasiswa PNJ Menciptakan Aplikasi Skrining Stroke Gratis",
        date = "1 jam lalu",
        source = "CNN Indonesia",
        imageUrl = "https://images.unsplash.com/photo-1586773860418-d37222d8fce3?q=80&w=1600&auto=format&fit=crop",
        category = "Headline",
        description = "Inovasi untuk deteksi dini stroke.",
        content = ""
    )

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        autoScrollHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_INTERVAL)
    }

    override fun onPause() {
        super.onPause()
        autoScrollHandler.removeCallbacks(autoScrollRunnable)
    }
}