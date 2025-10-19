package com.pkm.said

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pkm.said.adapter.ScreeningDetailAdapter
import com.pkm.said.databinding.FragmentScreeningHistoryDetailBinding
import com.pkm.said.screening.RiskLevel
import com.pkm.said.screening.ScreeningDataManager
import com.pkm.said.screening.ScreeningResult
import com.pkm.said.screening.ScreeningRepository
import com.pkm.said.screening.completedAtFormatted
import kotlinx.coroutines.launch

class ScreeningDetailFragment : Fragment() {

    private var _binding: FragmentScreeningHistoryDetailBinding? = null
    private val binding get() = _binding!!

    private var sessionId: String? = null
    private var screeningResult: ScreeningResult? = null
    private lateinit var testAdapter: ScreeningDetailAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionId = arguments?.getString("sessionId")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScreeningHistoryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupToolbar()
        setupRecycler()
        loadData()
        setupActions()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        val title = SpannableString("Detail Screening").apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, 0)
        }
        binding.tvScreenTitle.text = title
    }

    private fun setupRecycler() {
        testAdapter = ScreeningDetailAdapter()
        binding.rvDetails.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDetails.adapter = testAdapter
    }

    private fun loadData() {
        val id = sessionId
        if (id.isNullOrEmpty()) {
            showNotFound()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // PRIORITAS 1: Ambil langsung dari Firestore by sessionId
                screeningResult = ScreeningRepository.getScreeningById(id)

                if (screeningResult == null) {
                    // PRIORITAS 2: Fallback ke data lokal
                    screeningResult = ScreeningDataManager.getScreeningHistory(requireContext())
                        .find { it.sessionId == id && it.isCompleted }

                    if (screeningResult == null) {
                        showNotFound()
                        return@launch
                    }
                }

                showContent(screeningResult!!)

            } catch (e: Exception) {
                Log.e("ScreeningDetail", "Gagal memuat detail screening: ${e.message}")

                // Emergency fallback
                screeningResult = ScreeningDataManager.getScreeningHistory(requireContext())
                    .find { it.sessionId == id && it.isCompleted }

                if (screeningResult == null) {
                    showNotFound()
                } else {
                    showContent(screeningResult!!)
                }
            }
        }
    }

    private fun showNotFound() {
        binding.groupContent.isVisible = false
        binding.layoutNotFound.isVisible = true
    }

    @SuppressLint("SetTextI18n")
    private fun showContent(result: ScreeningResult) {
        binding.layoutNotFound.isVisible = false
        binding.groupContent.isVisible = true

        // FAST overall % (0..80)
        val befastPercent = ScreeningDataManager.calculateBEFASTOverallPercent(result)

        // Header
        binding.tvRiskTitle.text = result.overallRisk.displayName
        binding.tvBeFastCount.text = "FAST: ${completedTestCount(result)}/5"
        applyRiskHeader(result.overallRisk)

        // Waktu: utamakan completedAt (server) → fallback ke timestamp awal sesi
        val whenStr = result.completedAtFormatted().let { if (it == "-") result.timestamp else it }
        val (dateStr, timeStr) = splitDateTime(whenStr)
        binding.tvDate.text = dateStr
        binding.tvDateTime.text = timeStr

        // Lokasi dummy (bisa diisi dari testData kalau ada)
        binding.tvLocation.text = "Jakarta"

        // Body
        binding.progressBar.max = 100
        binding.tvPercent.text = "$befastPercent%"
        binding.progressBar.progress = befastPercent
        binding.tvRiskDescription.text = result.overallRisk.description

        // Footer
        binding.tvStatusValue.text = if (result.isCompleted) "Completed" else "In Progress"
        binding.tvRiskProbability.text = "$befastPercent% dari maks 100%"
        binding.tvStatusValue.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (result.isCompleted) R.color.success else R.color.GrayLight
            )
        )

        // Detail list test
        val testList = buildList {
            result.balanceResult?.let { add(it) }
            result.eyesResult?.let { add(it) }
            result.faceResult?.let { add(it) }
            result.armsResult?.let { add(it) }
//            result.speechResult?.let { add(it) }
        }
        testAdapter.submitList(testList)
    }

    private fun applyRiskHeader(risk: RiskLevel) {
        val ctx = requireContext()
        val bgRes = when (risk) {
            RiskLevel.CRITICAL, RiskLevel.HIGH -> R.drawable.bg_card_header_high
            RiskLevel.MEDIUM -> R.drawable.bg_card_header_medium
            RiskLevel.LOW -> R.drawable.bg_card_header_low
            RiskLevel.UNKNOWN -> R.drawable.bg_card_header_unknown
        }
        binding.headerContainer.setBackgroundResource(bgRes)
        val accentColor = when (risk) {
            RiskLevel.CRITICAL, RiskLevel.HIGH -> ContextCompat.getColor(ctx, R.color.warning_color)
            RiskLevel.MEDIUM -> ContextCompat.getColor(ctx, R.color.risk_medium_text)
            RiskLevel.LOW -> ContextCompat.getColor(ctx, R.color.risk_low_text)
            RiskLevel.UNKNOWN -> ContextCompat.getColor(ctx, R.color.risk_unknown_text)
        }
        binding.tvPercent.setTextColor(accentColor)
        binding.tvRiskProbability.setTextColor(accentColor)
        binding.progressBar.progressDrawable.setTint(accentColor)
        binding.progressBar.max = 100
    }

    private fun setupActions() {
        binding.btnShare.setOnClickListener {
            screeningResult?.let { shareResult(it) }
        }
        binding.btnDownload.setOnClickListener {
            // TODO: implement generate PDF / image
            showTemporaryMessage("Fitur unduh belum diimplementasi")
        }
    }

    private fun shareResult(result: ScreeningResult) {
        val fastPercent = ScreeningDataManager.calculateBEFASTOverallPercent(result)
        val whenStr = result.completedAtFormatted().let { if (it == "-") result.timestamp else it }
        val shareText = """
            Hasil FAST Screening:
            • Tanggal: $whenStr
            • Risiko: ${result.overallRisk.displayName}
            • FAST overall: $fastPercent% (maks 80%)
            
            ${result.overallRisk.description}
            #FAST #StrokeAwareness
        """.trimIndent()
        val sendIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(android.content.Intent.createChooser(sendIntent, "Bagikan hasil screening"))
    }

    private fun showTemporaryMessage(msg: String) {
        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun completedTestCount(result: ScreeningResult): Int {
        val tests = listOfNotNull(result.balanceResult, result.eyesResult, result.faceResult, result.armsResult) //, result.speechResult legacy
        return tests.count { it.isCompleted }
    }

    private fun splitDateTime(ts: String): Pair<String, String> {
        // input: "yyyy-MM-dd HH:mm:ss"
        return try {
            val inFmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val date = inFmt.parse(ts)
            val dayFmt = java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale("id"))
            val timeFmt = java.text.SimpleDateFormat("HH.mm", java.util.Locale.getDefault())
            dayFmt.format(date!!) to timeFmt.format(date)
        } catch (e: Exception) {
            ts to ""
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
