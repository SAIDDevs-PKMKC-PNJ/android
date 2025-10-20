package com.pkm.said.adapter

import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pkm.said.R
import com.pkm.said.databinding.ItemScreeningResultBinding
import com.pkm.said.screening.RiskLevel
import com.pkm.said.screening.ScreeningDataManager
import com.pkm.said.screening.ScreeningResult
import com.pkm.said.screening.TestResult
import com.pkm.said.screening.completedAtFormatted
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

            // Risk header
            binding.tvRiskTitle.text = item.overallRisk.displayName
            binding.tvRiskDesc.text  = item.overallRisk.description

            // Count BE-FAST /3
            binding.tvBeFastCount.text = "BE-FAST: ${completedCount(item)}/5"

            // Waktu: utamakan completedAt (server) → fallback ke timestamp (awal sesi)
            val whenStr = item.completedAtFormatted().let { if (it == "-") item.timestamp else it }
            binding.tvDate.text    = formatDate(whenStr)
            binding.tvDateTime.text = formatTime(whenStr)

            // Lokasi dummy (kalau nanti ada, ambil dari testData)
            binding.tvLocation.text = "Jakarta"

            // FAST overall 0..80%
            val fastOverall = ScreeningDataManager.calculateBEFASTOverallPercent(item)
            binding.tvPercent.text = "$fastOverall%"
            binding.progressBar.max = 100
            binding.progressBar.progress = fastOverall

            // Status
            binding.tvStatusValue.text = if (item.isCompleted) "Completed" else "In Progress"
            binding.tvRiskLabelValue.text = "$fastOverall% kemungkinan"
            val risk = item.overallRisk
            val colorRes = when (risk) {
                RiskLevel.CRITICAL, RiskLevel.HIGH -> R.color.risk_high_text
                RiskLevel.MEDIUM -> R.color.risk_medium_text
                RiskLevel.LOW -> R.color.risk_low_text
                else -> R.color.risk_unknown_text
            }
            binding.tvRiskLabelValue.setTextColor(ContextCompat.getColor(ctx, colorRes))

            // Buttons
            binding.btnDetail.setOnClickListener { onDetail(item) }
            binding.btnShare.setOnClickListener { onShare(item) }

            // Style kartu berdasarkan risk
            applyRiskStyle(risk)

            // Bold kalau completed
            binding.tvStatusValue.setTypeface(null, if (item.isCompleted) Typeface.BOLD else Typeface.NORMAL)
        }

        private fun completedCount(item: ScreeningResult): Int {
            val tests = listOfNotNull(item.balanceResult, item.eyesResult, item.faceResult, item.armsResult) //, item.speechResult
            return tests.count { it.isCompleted }
        }

        // (TETAPKAN kalau kamu masih butuh, tapi tidak dipakai untuk FAST overall)
        @Suppress("unused")
        private fun computePercentLegacy(item: ScreeningResult): Int {
            val tests: List<TestResult> = listOfNotNull(item.balanceResult, item.eyesResult, item.faceResult, item.armsResult) //, item.speechResult`````````````````````````````````````````
            if (tests.isEmpty()) return 0
            val avg = tests.map { it.score }.average()
            return (avg * 100).roundToInt().coerceIn(0, 100)
        }

        private fun formatDate(ts: String): String {
            return try {
                val inFmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                val date = inFmt.parse(ts)
                val dayFmt = java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale("id"))
                dayFmt.format(date!!)
            } catch (e: Exception) {
                ts
            }
        }

        private fun formatTime(ts: String): String {
            return try {
                val inFmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                val date = inFmt.parse(ts)
                val timeFmt = java.text.SimpleDateFormat("HH.mm", java.util.Locale.getDefault())
                timeFmt.format(date!!)
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
            setProgressColor(binding.progressBar, progressTint)
        }

        @Suppress("DEPRECATION")
        private fun setProgressColor(progressBar: ProgressBar, colorRes: Int) {
            try {
                val color = ContextCompat.getColor(progressBar.context, colorRes)

                // Dapatkan progress drawable
                val progressDrawable = progressBar.progressDrawable

                // ✅ HANYA ubah bagian progress, bukan background
                if (progressDrawable is LayerDrawable) {
                    val progressLayer = progressDrawable.findDrawableByLayerId(android.R.id.progress)
                    progressLayer?.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
                } else {
                    // Fallback: set color filter ke seluruh drawable
                    progressDrawable.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
                }

            } catch (e: Exception) {
                Log.e("ProgressColor", "Error setting progress color: ${e.message}")
            }
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
