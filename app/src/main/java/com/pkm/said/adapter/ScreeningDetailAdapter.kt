package com.pkm.said.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pkm.said.R
import com.pkm.said.databinding.ItemScreeningDetailBinding
import com.pkm.said.screening.TestResult
import kotlin.math.roundToInt

class ScreeningDetailAdapter :
    ListAdapter<TestResult, ScreeningDetailAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TestResult>() {
            override fun areItemsTheSame(oldItem: TestResult, newItem: TestResult): Boolean =
                oldItem.testName == newItem.testName && oldItem.timestamp == newItem.timestamp

            override fun areContentsTheSame(oldItem: TestResult, newItem: TestResult): Boolean =
                oldItem == newItem
        }
    }

    inner class VH(private val binding: ItemScreeningDetailBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TestResult) {
            val ctx = binding.root.context // ✅ PERBAIKI: gunakan ctx bukan context

            // Nama tes
            binding.tvTestName.text = getDisplayName(item.testName)

            when {
                !item.isCompleted -> {
                    // Tes belum selesai
                    binding.tvPercent.text = "—%"
                    binding.tvTestName.setTextColor(ContextCompat.getColor(ctx, R.color.GrayLight)) // ✅ PERBAIKI: ctx
                    setDotIndicatorColor(R.color.GrayLight)
                }
                item.isSuccessful -> {
                    // Tes selesai dan normal
                    val percent = (item.score.coerceIn(0f, 1f) * 100f).roundToInt()
                    binding.tvPercent.text = "$percent%"

                    // ✅ Warna berdasarkan score meski successful
                    val (textColor, dotColor) = getColorsForScore(item.score) // ✅ PERBAIKI: hapus parameter isSuccessful
                    binding.tvTestName.setTextColor(ContextCompat.getColor(ctx, textColor)) // ✅ PERBAIKI: ctx
                    binding.tvPercent.setTextColor(ContextCompat.getColor(ctx, textColor)) // ✅ PERBAIKI: ctx
                    setDotIndicatorColor(dotColor)
                }
                else -> {
                    // Tes selesai dan abnormal
                    val percent = (item.score.coerceIn(0f, 1f) * 100f).roundToInt()
                    binding.tvPercent.text = "$percent%"

                    // ✅ Warna berdasarkan score untuk abnormal
                    val (textColor, dotColor) = getColorsForScore(item.score) // ✅ PERBAIKI: hapus parameter isSuccessful
                    binding.tvTestName.setTextColor(ContextCompat.getColor(ctx, textColor)) // ✅ PERBAIKI: ctx
                    binding.tvPercent.setTextColor(ContextCompat.getColor(ctx, textColor)) // ✅ PERBAIKI: ctx
                    setDotIndicatorColor(dotColor)
                }
            }
        }

        private fun setDotIndicatorColor(colorRes: Int) {
            ImageViewCompat.setImageTintList(
                binding.dotIndicator,
                ColorStateList.valueOf(ContextCompat.getColor(binding.root.context, colorRes))
            )
        }

        // ✅ PERBAIKI: Hanya return 2 values (Pair) bukan Triple
        private fun getColorsForScore(score: Float): Pair<Int, Int> {
            return when {
                score < 0.3f -> Pair(
                    R.color.risk_low_text,      // Text color - Hijau
                    R.color.risk_low_text       // Dot color - Hijau
                )
                score < 0.6f -> Pair(
                    R.color.risk_medium_text,   // Text color - Orange
                    R.color.risk_medium_text    // Dot color - Orange
                )
                else -> Pair(
                    R.color.risk_high_text,     // Text color - Merah
                    R.color.risk_high_text      // Dot color - Merah
                )
            }
        }

        // ✅ Mapping nama test yang konsisten dengan ScreeningDataManager
        private fun getDisplayName(testName: String): String {
            return when (testName.lowercase()) {
                "balance_test", "balance", "befast_balance", "b" -> "B - Balance Test"
                "eyes_test", "eyes", "befast_eyes", "e" -> "E - Eyes Test"
                "face_test", "face", "befast_face", "f" -> "F - Face Test"
                "arms_test", "arms", "arm_test", "arm", "befast_arms", "a" -> "A - Arms Test"
                "speech_test", "speech", "voice", "befast_speech", "s" -> "S - Speech Test"
                else -> testName.replace('_', ' ').replaceFirstChar { it.uppercase() }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemScreeningDetailBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
}