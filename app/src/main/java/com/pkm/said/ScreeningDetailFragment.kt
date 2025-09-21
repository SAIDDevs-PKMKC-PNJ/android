package com.pkm.said

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.pkm.said.MainActivity
import com.pkm.said.R
import com.pkm.said.adapter.ScreeningDetailAdapter
import com.pkm.said.databinding.FragmentScreeningHistoryDetailBinding
import com.pkm.said.screening.RiskLevel
import com.pkm.said.screening.ScreeningDataManager
import com.pkm.said.screening.ScreeningResult
import com.pkm.said.screening.TestResult
import kotlin.math.roundToInt

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
        val allHistory = ScreeningDataManager.getScreeningHistory(requireContext())
        screeningResult = allHistory.firstOrNull { it.sessionId == sessionId }

        if (screeningResult == null) {
            binding.groupContent.isVisible = false
            binding.layoutNotFound.isVisible = true
            return
        }

        binding.layoutNotFound.isVisible = false
        binding.groupContent.isVisible = true

        val result = screeningResult!!

        val percent = computeOverallPercent(result)

        // Header
        binding.tvRiskTitle.text = result.overallRisk.displayName
        binding.tvBeFastCount.text = "BE-FAST: ${completedTestCount(result)}/5"
        applyRiskHeader(result.overallRisk)

        // Body
        binding.tvDateTime.text = formatDateTime(result.timestamp)
        binding.tvLocation.text = "Jakarta" // TODO: ganti jika punya data lokasi

        binding.tvPercent.text = "$percent%"
        binding.progressBar.progress = percent
        binding.tvRiskDescription.text = result.overallRisk.description

        // Footer
        binding.tvStatusValue.text = if (result.isCompleted) "Completed" else "In Progress"
        binding.tvRiskProbability.text = "$percent% kemungkinan"
        binding.tvStatusValue.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (result.isCompleted) R.color.risk_low_text else R.color.GrayLight
            )
        )

        // Detail list test
        val testList = mutableListOf<TestResult>()
        result.faceResult?.let { testList.add(it) }
        result.armsResult?.let { testList.add(it) }
        result.speechResult?.let { testList.add(it) }
        // Placeholder untuk 2 test lain (Balance, Eyes/Time) jika di masa depan mau ditambah
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
            RiskLevel.CRITICAL, RiskLevel.HIGH -> ContextCompat.getColor(ctx, R.color.risk_high_text)
            RiskLevel.MEDIUM -> ContextCompat.getColor(ctx, R.color.risk_medium_text)
            RiskLevel.LOW -> ContextCompat.getColor(ctx, R.color.risk_low_text)
            RiskLevel.UNKNOWN -> ContextCompat.getColor(ctx, R.color.risk_unknown_text)
        }
        binding.tvRiskTitle.setTextColor(accentColor)
        binding.tvPercent.setTextColor(accentColor)
        binding.tvRiskProbability.setTextColor(accentColor)
        binding.progressBar.progressDrawable.setTint(accentColor)
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
        val percent = computeOverallPercent(result)
        val shareText = """
            Hasil FAST Screening:
            • Tanggal: ${result.timestamp}
            • Risiko: ${result.overallRisk.displayName}
            • Perkiraan: $percent%
            
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

    private fun computeOverallPercent(result: ScreeningResult): Int {
        val tests = listOfNotNull(result.faceResult, result.armsResult, result.speechResult)
        if (tests.isEmpty()) return 0
        val avg = tests.map { it.score }.average()
        return (avg * 100).roundToInt().coerceIn(0, 100)
    }

    private fun completedTestCount(result: ScreeningResult): Int {
        val tests = listOfNotNull(result.faceResult, result.armsResult, result.speechResult)
        return tests.count { it.isCompleted }
    }

    private fun formatDateTime(ts: String): String {
        // ts format "yyyy-MM-dd HH:mm:ss"
        // Output contoh: "08 September 2025 • 14.00"
        return try {
            val inFmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val date = inFmt.parse(ts)
            val dayFmt = java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale("id"))
            val timeFmt = java.text.SimpleDateFormat("HH.mm", java.util.Locale.getDefault())
            "${dayFmt.format(date!!) } • ${timeFmt.format(date)}"
        } catch (e: Exception) {
            ts
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}