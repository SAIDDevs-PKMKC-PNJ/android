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
        val id = sessionId
        screeningResult = if (id.isNullOrEmpty()) null
        else ScreeningDataManager.getScreeningById(requireContext(), id)

        if (screeningResult == null) {
            binding.groupContent.isVisible = false
            binding.layoutNotFound.isVisible = true
            return
        }

        binding.layoutNotFound.isVisible = false
        binding.groupContent.isVisible = true

        val result = screeningResult!!

        // FAST overall % (0..80)
        val fastPercent = ScreeningDataManager.calculateFASTOverallPercent(result)

        // Header
        binding.tvRiskTitle.text = result.overallRisk.displayName
        binding.tvBeFastCount.text = "FAST: ${completedTestCount(result)}/3"
        applyRiskHeader(result.overallRisk)

        // Body
        val (dateStr, timeStr) = splitDateTime(result.timestamp)
        binding.tvDate.text = dateStr
        binding.tvDateTime.text = timeStr
        binding.tvLocation.text = "Jakarta"

        binding.progressBar.max = 80
        binding.tvPercent.text = "$fastPercent%"
        binding.progressBar.progress = fastPercent
        binding.tvRiskDescription.text = result.overallRisk.description

        // Footer
        binding.tvStatusValue.text = if (result.isCompleted) "Completed" else "In Progress"
        binding.tvRiskProbability.text = "$fastPercent% dari maks 80%"
        binding.tvStatusValue.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (result.isCompleted) R.color.success else R.color.GrayLight
            )
        )

        // Detail list test
        val testList = buildList {
            result.faceResult?.let { add(it) }
            result.armsResult?.let { add(it) }
            result.speechResult?.let { add(it) }
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
        val fastPercent = ScreeningDataManager.calculateFASTOverallPercent(result)
        val shareText = """
            Hasil FAST Screening:
            • Tanggal: ${result.timestamp}
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
        val tests = listOfNotNull(result.faceResult, result.armsResult, result.speechResult)
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
