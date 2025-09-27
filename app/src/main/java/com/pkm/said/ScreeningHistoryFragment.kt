package com.pkm.said

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.pkm.said.databinding.FragmentScreeningHistoryBinding
import com.pkm.said.screening.RiskLevel
import com.pkm.said.screening.ScreeningDataManager
import com.pkm.said.screening.ScreeningResult
import com.pkm.said.adapter.ScreeningHistoryAdapter

class ScreeningHistoryFragment : Fragment() {

    private var _binding: FragmentScreeningHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ScreeningHistoryAdapter
    private var fullHistory: List<ScreeningResult> = emptyList()
    private var currentFilter: RiskLevel? = null  // null = semua

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScreeningHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupToolbar()
        setupRecycler()
        setupChips()
        loadData()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        val title = "Riwayat Screening"
        val span = SpannableString(title).apply {
            setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, title.length, 0)
        }
        binding.tvTitle.text = span
    }

    private fun setupRecycler() {
        adapter = ScreeningHistoryAdapter(
            onDetail = { result ->
                val bundle = Bundle().apply { putString("sessionId", result.sessionId) }
                findNavController().navigate(R.id.navigation_historyDetail, bundle)
            },
            onShare = { result ->
                shareResult(result)
            }
        )
        binding.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHistory.adapter = adapter
    }

    private fun setupChips() {
        // All
        binding.chipAll.setOnClickListener {
            selectFilter(null)
        }
        // High
        binding.chipHigh.setOnClickListener {
            selectFilter(RiskLevel.HIGH) // atau CRITICAL juga? Bisa tambah chip sendiri
        }
        binding.chipMedium.setOnClickListener {
            selectFilter(RiskLevel.MEDIUM)
        }
        binding.chipLow.setOnClickListener {
            selectFilter(RiskLevel.LOW)
        }
    }

    private fun selectFilter(risk: RiskLevel?) {
        currentFilter = risk
        applyFilter()
        updateChipSelection()
    }

    private fun updateChipSelection() {
        fun Chip.mark(selected: Boolean) {
            isChecked = selected
        }
        binding.chipAll.mark(currentFilter == null)
        binding.chipHigh.mark(currentFilter == RiskLevel.HIGH)
        binding.chipMedium.mark(currentFilter == RiskLevel.MEDIUM)
        binding.chipLow.mark(currentFilter == RiskLevel.LOW)
    }

    private fun loadData() {
        fullHistory = ScreeningDataManager.getScreeningHistory(requireContext())

        binding.layoutEmpty.isVisible = fullHistory.isEmpty()
        binding.recyclerHistory.isVisible = fullHistory.isNotEmpty()

        applyFilter()
    }

    private fun applyFilter() {
        val list = when (currentFilter) {
            null -> fullHistory
            RiskLevel.HIGH -> fullHistory.filter { it.overallRisk == RiskLevel.HIGH || it.overallRisk == RiskLevel.CRITICAL }
            else -> fullHistory.filter { it.overallRisk == currentFilter }
        }
        adapter.submitList(list)
        binding.tvCount.text = getString(R.string.session, list.size)
        binding.layoutEmpty.isVisible = list.isEmpty()
        binding.recyclerHistory.isVisible = list.isNotEmpty()
    }

    private fun shareResult(result: ScreeningResult) {
        val percent = computeOverallPercent(result)
        val shareText = """
            Hasil FAST Screening:
            - Tanggal: ${result.timestamp}
            - Risiko: ${result.overallRisk.displayName}
            - Perkiraan: ${percent}%
            #FAST #StrokeAwareness
        """.trimIndent()
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(intent, "Bagikan hasil screening"))
    }

    private fun computeOverallPercent(result: ScreeningResult): Int {
        return ScreeningDataManager.calculateFASTOverallPercent(result)
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}