package com.pkm.said.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pkm.said.R
import com.pkm.said.databinding.ItemScreeningResultBinding
import com.pkm.said.screening.RiskLevel
import com.pkm.said.screening.ScreeningResult
import com.pkm.said.screening.TestResult
import kotlin.math.roundToInt

class ScreeningHistoryAdapter(
    private val onDetail: (ScreeningResult) -> Unit,
    private val onShare: (ScreeningResult) -> Unit
) : ListAdapter<ScreeningResult, ScreeningHistoryAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ScreeningResult>() {
            override fun areItemsTheSame(oldItem: ScreeningResult, newItem: ScreeningResult) =
                oldItem.sessionId == newItem.sessionId

            override fun areContentsTheSame(oldItem: ScreeningResult, newItem: ScreeningResult) =
                oldItem == newItem
        }
    }

    inner class VH(val binding: ItemScreeningResultBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScreeningResult) {
            val ctx = binding.root.context

            binding.tvRiskTitle.text = item.overallRisk.displayName
            binding.tvBeFastCount.text = "BE-FAST: ${completedCount(item)}/5"

            // Dummy location (bisa diambil dari testData jika ada)
            binding.tvDate.text = formatDateTime(item.timestamp)
            binding.tvLocation.text = "Jakarta"

            val percent = computePercent(item)
            binding.tvPercent.text = "$percent%"
            binding.progressBar.progress = percent

            binding.tvRiskDesc.text = item.overallRisk.description

            // Status test
            binding.tvStatusValue.text = if (item.isCompleted) "Completed" else "In Progress"
            binding.tvRiskLabelValue.text = "$percent% kemungkinan"

            // Buttons
            binding.btnDetail.setOnClickListener { onDetail(item) }
            binding.btnShare.setOnClickListener { onShare(item) }

            // Set warna sesuai risk
            applyRiskStyle(item.overallRisk)

            // Completed styling
            binding.tvStatusValue.setTypeface(null, if (item.isCompleted) Typeface.BOLD else Typeface.NORMAL)
        }

        private fun completedCount(item: ScreeningResult): Int {
            val tests = listOfNotNull(item.faceResult, item.armsResult, item.speechResult)
            return tests.count { it.isCompleted } // currently 3 tests; displayed as /5 untuk desain
        }

        private fun computePercent(item: ScreeningResult): Int {
            val tests: List<TestResult> = listOfNotNull(item.faceResult, item.armsResult, item.speechResult)
            if (tests.isEmpty()) return 0
            val avg = tests.map { it.score }.average()
            return (avg * 100).roundToInt().coerceIn(0, 100)
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

        private fun applyRiskStyle(risk: RiskLevel) {
            val ctx = binding.root.context
            val (bgHeader, accent, progressTint) = when (risk) {
                RiskLevel.CRITICAL, RiskLevel.HIGH -> Triple(
                    R.drawable.bg_card_header_high,
                    ContextCompat.getColor(ctx, R.color.test_abnormal),
                    ContextCompat.getColor(ctx, R.color.test_abnormal)
                )
                RiskLevel.MEDIUM -> Triple(
                    R.drawable.bg_card_header_medium,
                    ContextCompat.getColor(ctx, R.color.warning_color),
                    ContextCompat.getColor(ctx, R.color.warning_color)
                )
                RiskLevel.LOW -> Triple(
                    R.drawable.bg_card_header_low,
                    ContextCompat.getColor(ctx, R.color.success),
                    ContextCompat.getColor(ctx, R.color.success)
                )
                RiskLevel.UNKNOWN -> Triple(
                    R.drawable.bg_card_header_unknown,
                    ContextCompat.getColor(ctx, R.color.risk_unknown_text),
                    ContextCompat.getColor(ctx, R.color.risk_unknown_text)
                )
            }

            binding.headerContainer.setBackgroundResource(bgHeader)
            binding.tvPercent.setTextColor(accent)
            binding.tvRiskLabelValue.setTextColor(accent)

            binding.progressBar.progressDrawable.setTint(progressTint)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemScreeningResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
}