package com.pkm.said

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.pkm.said.databinding.FragmentScreeningHistoryBinding
import com.pkm.said.screening.RiskLevel
import com.pkm.said.screening.ScreeningDataManager
import com.pkm.said.screening.ScreeningRepository
import com.pkm.said.screening.ScreeningResult
import com.pkm.said.adapter.ScreeningHistoryAdapter
import com.pkm.said.screening.completedAtFormatted
import com.pkm.said.util.AuthManager
import kotlinx.coroutines.launch

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
        _binding?.let { b ->
            b.btnBack.setOnClickListener { findNavController().navigateUp() }
            val title = "Riwayat Screening"
            val span = SpannableString(title).apply {
                setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, title.length, 0)
            }
            b.tvTitle.text = span
        }
    }

    private fun setupRecycler() {
        adapter = ScreeningHistoryAdapter(
            onDetail = { result ->
                val bundle = Bundle().apply { putString("sessionId", result.sessionId) }
                findNavController().navigate(R.id.navigation_historyDetail, bundle)
            },
            onShare = { result -> shareResult(result) }
        )
        _binding?.let { b ->
            b.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
            b.recyclerHistory.adapter = adapter
        }
    }

    private fun setupChips() {
        _binding?.let { b ->
            // All
            b.chipAll.setOnClickListener { selectFilter(null) }
            // High (termasuk CRITICAL)
            b.chipHigh.setOnClickListener { selectFilter(RiskLevel.HIGH) }
            b.chipMedium.setOnClickListener { selectFilter(RiskLevel.MEDIUM) }
            b.chipLow.setOnClickListener { selectFilter(RiskLevel.LOW) }

            b.chipAll.isChecked = true
        }
    }

    private fun selectFilter(risk: RiskLevel?) {
        currentFilter = risk
        applyFilter()
        updateChipSelection()
    }

    private fun updateChipSelection() {
        _binding?.let { b ->
            b.chipAll.isChecked = currentFilter == null
            b.chipHigh.isChecked = currentFilter == RiskLevel.HIGH
            b.chipMedium.isChecked = currentFilter == RiskLevel.MEDIUM
            b.chipLow.isChecked = currentFilter == RiskLevel.LOW
        }
    }

    private fun loadData() {
        setLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // NEW: Langsung ambil dari Firestore (data permanen)
                fullHistory = ScreeningRepository.getScreeningHistory(limit = 50)

                // NEW: Jika kosong, coba fallback ke data lokal yang completed
                if (fullHistory.isEmpty()) {
                    fullHistory = ScreeningDataManager.getScreeningHistory(requireContext())
                        .filter { it.isCompleted }
                        .take(50)
                }

            } catch (e: Exception) {
                // NEW: Error handling yang lebih baik
                Log.e("ScreeningHistory", "Gagal memuat riwayat: ${e.message}")

                // Fallback ke data lokal saja
                fullHistory = ScreeningDataManager.getScreeningHistory(requireContext())
                    .filter { it.isCompleted }
                    .take(50)

                // Tampilkan pesan error
                if (fullHistory.isEmpty()) {
                    showErrorState("Gagal memuat data")
                } else {
                    showWarningState("Menggunakan data lokal")
                }
            } finally {
                setLoading(false)
                applyFilter()
            }
        }
    }
    private fun setLoading(loading: Boolean) {
        _binding?.let { b ->
            b.loadingOverlay.isVisible = loading
            b.recyclerHistory.isVisible = !loading && fullHistory.isNotEmpty()
            b.layoutEmpty.isVisible = !loading && fullHistory.isEmpty()
            b.layoutError.isVisible = false
            b.layoutWarning.isVisible = false
        }
    }

    private fun showErrorState(message: String) {
        _binding?.let { b ->
            b.layoutError.isVisible = true
            b.tvErrorText.text = message
            b.recyclerHistory.isVisible = false
            b.layoutEmpty.isVisible = false
        }
    }

    private fun showWarningState(message: String) {
        _binding?.let { b ->
            b.layoutWarning.isVisible = true
            b.tvWarningText.text = message
            b.btnRetry.setOnClickListener { loadData() }
        }
    }

    private fun applyFilter() {
        _binding?.let { b ->
            val list = when (currentFilter) {
                null -> fullHistory
                RiskLevel.HIGH -> fullHistory.filter {
                    it.overallRisk == RiskLevel.HIGH || it.overallRisk == RiskLevel.CRITICAL
                }
                RiskLevel.MEDIUM -> fullHistory.filter { it.overallRisk == RiskLevel.MEDIUM }
                RiskLevel.LOW -> fullHistory.filter { it.overallRisk == RiskLevel.LOW }
                else -> fullHistory.filter { it.overallRisk == currentFilter }
            }
            adapter.submitList(list)
            b.tvCount.text = getString(R.string.session, list.size)
            b.layoutEmpty.isVisible = list.isEmpty()
            b.recyclerHistory.isVisible = list.isNotEmpty()
            if (list.isNotEmpty()) {
                b.layoutError.isVisible = false
                b.layoutWarning.isVisible = false
            }
        }
    }

    private fun shareResult(result: ScreeningResult) {
        val percent = ScreeningDataManager.calculateBEFASTOverallPercent(result)
        val whenText = result.completedAtFormatted()
        val shareText = """
            Hasil FAST Screening:
            - Tanggal: $whenText
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

    override fun onResume() {
        super.onResume()
        loadData()
        if (!AuthManager.ensureUserLoggedIn(requireActivity())) return
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
