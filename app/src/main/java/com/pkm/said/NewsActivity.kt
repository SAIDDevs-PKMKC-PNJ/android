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
        loadDataFromApi() // << gunakan API, bukan dummy
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
        val q = """
            (stroke OR Stroke OR "TIA" OR "transient ischemic attack" OR "gejala stroke" OR "pencegahan stroke" OR "FAST stroke") NOT ("The Strokes" OR golf OR tennis OR cricket OR "heat stroke" OR sunstroke OR heatwave)""".trimIndent()

        // Batasi pencarian ke title+description biar lebih relevan
        val searchIn = "title,description"

        val supported = setOf(
            "id",
            "ar",
            "de",
            "en",
            "es",
            "fr",
            "he",
            "it",
            "nl",
            "no",
            "pt",
            "ru",
            "sv",
            "ud",
            "zh"
        )
        val deviceLang = Locale.getDefault().language // misalnya "id"
        val apiLang = if (deviceLang in supported) deviceLang else "en"

        // (Opsional) batasi domain Indonesia yang sering bahas kesehatan
        val domainsId = listOf(
            "kompas.com", "health.detik.com", "cnnindonesia.com", "tempo.co",
            "alodokter.com", "klikdokter.com", "hellosehat.com", "liputan6.com",
            "suara.com", "tribunnews.com", "kumparan.com", "antaranews.com"
        ).joinToString(",")

        val to = java.time.OffsetDateTime.now().toString()
        val from = java.time.LocalDate.now().minusDays(30).toString()

        showLoading(true)

        NewsRetrofit.api.searchEverything(
            q = q,
            language = apiLang,
            domains = domainsId,
            searchIn = searchIn,
            sortBy = "publishedAt",
            to = to,
            from = from,
            page = 1,
            pageSize = 40,
            apiKey = apiKey
        ).enqueue(object : Callback<NewsResponse> {
            override fun onResponse(call: Call<NewsResponse>, response: Response<NewsResponse>) {
                val list = response.body()?.articles.orEmpty().map { it.toArticleItem() }
                    .filter { it.title.isNotBlank() }
                if (response.isSuccessful && list.isNotEmpty()) {
                    bindArticlesToUi(response.body()!!.articles!!)
                } else {
                    // 2nd: tanpa domains (lebih luas)
                    NewsRetrofit.api.searchEverything(
                        q = q,
                        language = apiLang,
                        domains = domainsId,
                        searchIn = searchIn,
                        sortBy = "publishedAt",
                        to = to,
                        from = from,
                        page = 1,
                        pageSize = 40,
                        apiKey = apiKey
                    ).enqueue(object : Callback<NewsResponse> {
                        override fun onResponse(
                            call2: Call<NewsResponse>,
                            res2: Response<NewsResponse>
                        ) {
                            val list2 = res2.body()?.articles.orEmpty().map { it.toArticleItem() }
                                .filter { it.title.isNotBlank() }
                            if (res2.isSuccessful && list2.isNotEmpty()) {
                                bindArticlesToUi(res2.body()!!.articles!!)
                            } else {
                                // 3rd: English fallback
                                fetchFallbackEn(q, apiKey, searchIn)
                            }
                        }

                        override fun onFailure(call2: Call<NewsResponse>, t: Throwable) {
                            fetchFallbackEn(q, apiKey, searchIn)
                        }
                    })
                }
            }

            override fun onFailure(call: Call<NewsResponse>, t: Throwable) {
                fetchFallbackEn(q, apiKey, searchIn)
            }
        })
    }

    private fun fetchFallbackEn(q: String, apiKey: String, searchIn: String) {
        NewsRetrofit.api.searchEverything(
            q = q,
            language = "en",
            sortBy = "publishedAt",
            page = 1,
            pageSize = 40,
            apiKey = apiKey
        ).enqueue(object : Callback<NewsResponse> {
            override fun onResponse(call: Call<NewsResponse>, response: Response<NewsResponse>) {
                val articles =
                    response.body()?.articles.orEmpty().filter { !it.title.isNullOrBlank() }
                if (!response.isSuccessful || articles.isEmpty()) {
                    // LAST RESORT: dummy minimal 1 agar UI tidak repetitif
                    showLoading(false)
                    val dummy = listOf(
                        placeholderArticle(1, "Headline").copy(
                            title = "Kenali Gejala FAST: Cara Cepat Deteksi Stroke",
                            description = "Fokus pada Face drooping, Arm weakness, Speech difficulty, Time to call.",
                            source = "SAID",
                            date = java.time.LocalDate.now().toString()
                        )
                    )
                    sliderAdapter.submitList(dummy)
                    edukasiAdapter.submitList(listOf(placeholderArticle(1, "Edukasi")))
                    pencegahanAdapter.submitList(listOf(placeholderArticle(1, "Pencegahan")))
                    Toast.makeText(
                        this@NewsActivity,
                        "Menampilkan konten default karena hasil kosong",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                bindArticlesToUi(articles)
            }

            override fun onFailure(call: Call<NewsResponse>, t: Throwable) {
                showLoading(false)
                val dummy = listOf(placeholderArticle(1, "Headline"))
                sliderAdapter.submitList(dummy)
                edukasiAdapter.submitList(listOf(placeholderArticle(1, "Edukasi")))
                pencegahanAdapter.submitList(listOf(placeholderArticle(1, "Pencegahan")))
                Toast.makeText(
                    this@NewsActivity,
                    "Gagal memuat berita. Menampilkan dummy.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }


    // ====== Helper placeholder ======
    private fun placeholderArticle(id: Int, category: String) = ArticleItem(
        id = "ph-$category-$id",
        title = when (category) {
            "Edukasi" -> "Apa Itu Stroke?\n" +
                    "Stroke terjadi ketika aliran darah ke otak terhambat, baik karena pembuluh darah tersumbat (stroke iskemik) atau pembuluh darah pecah (stroke hemoragik). Kekurangan oksigen dan nutrisi menyebabkan sel-sel otak mati, yang dapat mengakibatkan kecacatan permanen, bahkan kematian. \n" +
                    "Mengenali Gejala dengan Metode FAST\n" +
                    "Metode FAST adalah panduan mudah untuk mengenali tanda-tanda awal stroke: \n" +
                    "F ace (Wajah): Perhatikan jika ada sisi wajah yang terlihat terkulai, lemah, atau tidak simetris, terutama saat diminta tersenyum. \n" +
                    "A rm (Lengan): Minta orang tersebut untuk mengangkat kedua lengan. Jika salah satu lengan terasa lemah, terjatuh, atau tidak bisa diangkat, itu bisa menjadi tanda stroke. \n" +
                    "S peech (Bicara): Amati apakah ada kesulitan berbicara, seperti bicara cadel atau tidak jelas. Tanyakan mereka untuk mengulang kalimat sederhana. \n" +
                    "T ime (Waktu): Jika Anda melihat salah satu gejala di atas, segera hubungi layanan darurat (misalnya 119 di Indonesia) atau bawa ke rumah sakit terdekat. Waktu sangat penting dalam penanganan stroke. \n" +
                    "Mengapa Waktu Krusial?\n" +
                    "Semakin cepat penanganan medis diberikan, semakin besar peluang untuk meminimalkan kerusakan otak dan meningkatkan peluang pemulihan. "

            "Pencegahan" -> "Stroke merupakan penyakit yang sangat berisiko dan mengancam nyawa. Padahal, kita bisa mencegahnya dengan langkah-langkah sederhana, seperti mengenali faktor risikonya, menjalani gaya hidup sehat, serta melakukan deteksi dini melalui pemeriksaan komprehensif. Tiga hal ini dapat membantu menurunkan risiko komplikasi stroke di masa mendatang.\n" +
                    "\n" +
                    "Menurut dr. Maria Octaviany, Sp.N, Dokter Spesialis Neurologi Mayapada Hospital Bandung (MHBD), terdapat dua jenis faktor risiko stroke: yang dapat dihindari dan yang tidak dapat dihindari.\n" +
                    "\n" +
                    " \n" +
                    "\n" +
                    "Apa saja faktor risiko yang tidak dapat dihindari?\n" +
                    "\n" +
                    "Faktor-faktor ini bersifat alami, seperti usia, jenis kelamin, berat badan lahir rendah, ras dan etnis, serta faktor genetik.\n" +
                    "\n" +
                    "“Angka kejadian stroke meningkat seiring bertambahnya usia, bahkan dua kali lipat setelah usia 55 tahun. Pasien usia tua memiliki tingkat mortalitas yang lebih tinggi dan outcome penyakit yang lebih buruk dibandingkan pasien usia muda,” jelas dr. Maria.\n" +
                    "\n" +
                    "Jenis kelamin juga turut memengaruhi risiko stroke. Pada usia dewasa muda, pria memiliki risiko lebih tinggi, namun seiring bertambahnya usia dan memasuki masa menopause, risiko pada perempuan meningkat.\n+" +
                    "Lalu, bagaimana dengan faktor risiko yang bisa dicegah?\n" +
                    "\n" +
                    "Faktor risiko yang bisa diubah antara lain gaya hidup sedentari (minim aktivitas fisik), pola makan tidak sehat, kebiasaan merokok dan konsumsi alkohol, serta penyakit seperti hipertensi, diabetes, kolesterol tinggi, dan penyakit jantung.\n" +
                    "\n" +
                    "Cara mencegahnya? Mulai dari yang sederhana.\n" +
                    "\n" +
                    "Pola makan sehat\n" +
                    "Konsumsi makanan dengan nutrisi lengkap seperti karbohidrat, protein, serat, dan mineral. Kurangi makanan tinggi lemak dan garam, serta perbanyak sayur dan buah. Dengan menjaga pola makan, berat badan lebih terkontrol dan risiko obesitas penyebab stroke dapat ditekan.\n" +
                    "\n" +
                    "Aktivitas fisik rutin\n" +
                    "“Perempuan dan laki-laki yang aktif bergerak 150–300 menit seminggu dengan intensitas sedang memiliki risiko stroke 25–30% lebih rendah,” ungkap dr. Maria.\n" +
                    "Olahraga seperti jalan cepat, berenang, dan bersepeda bisa jadi pilihan.\n" +
                    "\n" +
                    "Kelola stres dan emosi\n" +
                    "Emosi berlebihan bisa meningkatkan hormon epinefrin dan tekanan darah, memicu hipertensi. Kesehatan mental berpengaruh besar pada kesehatan jantung dan otak.\n" +
                    "\n" +
                    "Hindari rokok dan alkohol\n" +
                    "Zat berbahaya dalam rokok dan alkohol terbukti meningkatkan risiko stroke. Menghentikannya adalah langkah nyata untuk hidup lebih sehat.\n" +
                    "\n" +
                    "Kendalikan penyakit bawaan\n" +
                    "Jika Anda memiliki hipertensi, diabetes, atau kolesterol tinggi, pastikan rutin kontrol dan minum obat sesuai anjuran dokter."

            else -> "Update Seputar Stroke"
        },
        date = "—",
        source = "SAID",
        imageUrl = "", // biar pakai placeholder image di Glide
        category = category,
        description = "",
        author = "",
        content = when (category) {
            "Edukasi" -> "Stroke"
            "Pencegahan" -> "5 Langkah Sederhana Cegah Stroke"
            else -> "Update Seputar Stroke"
        },
        url = "",
        domain = ""

    )

    private fun ensureSize(
        targetSize: Int,
        base: List<ArticleItem>,
        category: String
    ): List<ArticleItem> {
        if (base.size >= targetSize) return base.take(targetSize)
        val result = base.toMutableList()
        var i = 0
        // 1) Daur ulang item yang ada
        while (result.size < targetSize && base.isNotEmpty()) {
            result += base[i % base.size].copy(
                id = "${base[i % base.size].id}-dup-${i}",
                category = category
            )
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

        Log.d(TAG, "raw=${raw.size}")

        // Map + filter artikel kosong
        val mapped = raw
            .map { it.toArticleItem() }
            .filter { it.title.isNotBlank() && it.id.isNotBlank() }

        Log.d(TAG, "mapped=${mapped.size}")

        if (mapped.isEmpty()) {
            val headlines = (1..3).map { placeholderArticle(it, "Headline") }
            val edu = (1..5).map { placeholderArticle(it, "Edukasi") }
            val prev = (1..5).map { placeholderArticle(it, "Pencegahan") }
            sliderAdapter.submitList(headlines)
            edukasiAdapter.submitList(edu)
            pencegahanAdapter.submitList(prev)
            return
        }

        val headlines = mapped.take(5).map { it.copy(category = "Headline") }
        val uniqIds = headlines.map { it.id }.toSet()
        Log.d(TAG, "headlines.size=${headlines.size}, uniqueIds=${uniqIds.size}")
        headlines.forEachIndexed { i, a -> Log.d(TAG, "H[$i] id=${a.id} title=${a.title}") }

        val headlinesUnique = headlines.distinctBy { it.id }
        val minSlides = 3
        val filledHeadlines = if (headlinesUnique.size >= minSlides) {
            headlinesUnique
        } else {
            val needed = minSlides - headlinesUnique.size
            headlinesUnique + (1..needed).map { placeholderArticle(it, "Headline") }
        }
        sliderAdapter.submitList(filledHeadlines)

        if (!firstHeadlinesShown) {
            binding.viewPager.post { attachMediator() }
            firstHeadlinesShown = true
        }

        // --- Klasifikasi sederhana via keyword (tetap) ---
        val edukasiKeywords = listOf(
            "apa itu", "jenis", "gejala", "definisi", "penyebab", "edukasi", "fakta", "panduan",
            "iskemik", "hemoragik", "stroke iskemik", "stroke hemoragik", "fast",
            "what is", "types", "symptoms", "definition", "causes", "education", "facts", "guide",
            "ischemic", "hemorrhagic", "tia", "transient ischemic attack", "signs", "recognize"
        )

        val pencegahanKeywords = listOf(
            "pencegahan", "mencegah", "tips", "gaya hidup", "diet", "olahraga", "kebiasaan",
            "kontrol tekanan darah", "kurangi garam", "berhenti merokok", "turunkan risiko",
            "prevention", "prevent", "tips", "lifestyle", "diet", "exercise", "habits",
            "blood pressure control", "low sodium", "quit smoking", "reduce risk", "risk reduction"
        )

        fun normalize(s: String): String {
            val lower = s.lowercase()
            val noDia = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
                .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            val noPunct = noDia.replace("[\\p{Punct}]".toRegex(), " ")
            return noPunct.replace("\\s+".toRegex(), " ").trim()
        }

        fun containsAny(text: String, keys: List<String>): Boolean {
            val t = " ${normalize(text)} "
            return keys.any { key ->
                val k = " ${normalize(key)} "
                t.contains(k)
            }
        }

        val normalizedArticles = mapped.map { art ->
            val blob = listOfNotNull(art.title, art.description, art.content).joinToString(" ")
            val isEdu = containsAny(blob, edukasiKeywords)
            val isPrev = containsAny(blob, pencegahanKeywords)
            when {
                isEdu && !isPrev -> art.copy(category = "Edukasi")
                !isEdu && isPrev -> art.copy(category = "Pencegahan")
                isEdu && isPrev -> art.copy(category = "Edukasi") // tie-break
                else -> art
            }
        }

        val rest = normalizedArticles.drop(headlines.size)
        val edukasiRaw =
            rest.filter { it.category == "Edukasi" } + mapped.filter { it.category == "Edukasi" }
        val pencegahanRaw =
            rest.filter { it.category == "Pencegahan" } + mapped.filter { it.category == "Pencegahan" }

        val edukasiList = ensureSize(targetSize = 5, base = edukasiRaw, category = "Edukasi")
        val pencegahanList =
            ensureSize(targetSize = 5, base = pencegahanRaw, category = "Pencegahan")

        Log.d(TAG, "edukasi=${edukasiList.size} pencegahan=${pencegahanList.size}")

        val fallbackEdukasi = edukasiList.ifEmpty {
            normalizedArticles.drop(5).take(6).map { it.copy(category = "Edukasi") }
        }

        val fallbackPencegahan = pencegahanList.ifEmpty {
            normalizedArticles.drop(5 + fallbackEdukasi.size).take(6)
                .map { it.copy(category = "Pencegahan") }
        }

        // ListAdapter sudah diff-ing; tidak perlu notifyDataSetChanged()
        edukasiAdapter.submitList(fallbackEdukasi) {
            binding.rvEdukasi.adapter = edukasiAdapter
        }
        pencegahanAdapter.submitList(fallbackPencegahan) {
            binding.rvPencegahan.adapter = pencegahanAdapter
        }
    }


    // Mapper API -> UI
    fun NewsArticle.toArticleItem(category: String = "Headline"): ArticleItem {
        val rawUrl = this.url.orEmpty()
        val domain = try {
            java.net.URI(rawUrl).host?.removePrefix("www.") ?: ""
        } catch (_: Exception) {
            ""
        }

        val stableId = when {
            rawUrl.isNotBlank() -> rawUrl.hashCode().toString()
            !title.isNullOrBlank() -> title.hashCode().toString()
            else -> System.nanoTime().toString()
        }

        return ArticleItem(
            id = stableId,
            title = title.orEmpty(),
            date = publishedAt.orEmpty(),
            author = author.orEmpty(),
            source = source?.name.orEmpty(),
            imageUrl = urlToImage.orEmpty(),
            category = category,
            description = description.orEmpty(),
            content = content.orEmpty(),
            url = rawUrl,
            domain = domain,
            language = this.language,     // isi jika field ini ada di NewsArticle
            sourceId = source?.id
        )
    }


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
