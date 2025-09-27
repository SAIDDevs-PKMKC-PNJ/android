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
            val ctx = binding.root.context

            // Nama tes
            binding.tvTestName.text = mapName(item.testName)

            // Persentase: score = severity 0..1
            // Bedakan 3 status UI: Pending (tidak selesai), Normal, Abnormal
            val (percentText, tintRes, nameColorRes) = when {
                !item.isCompleted -> Triple("—", R.color.GrayLight, R.color.GrayLight) // pending
                item.isSuccessful -> {
                    val p = (item.score * 100).roundToInt().coerceIn(0, 100)
                    Triple("$p%", R.color.warning, R.color.BluePrimary)
                }
                else -> {
                    val p = (item.score * 100).roundToInt().coerceIn(0, 100)
                    Triple("$p%", R.color.warning_color, R.color.BluePrimary)
                }
            }

            binding.tvPercent.text = percentText
            binding.tvTestName.setTextColor(ContextCompat.getColor(ctx, nameColorRes))

            // Tint indikator bulat (lebih aman daripada setColorFilter)
            ImageViewCompat.setImageTintList(
                binding.dotIndicator,
                ColorStateList.valueOf(ContextCompat.getColor(ctx, tintRes))
            )
        }

        private fun mapName(raw: String): String = when (raw.lowercase()) {
            "face_test", "face", "befast_face" -> "Face Test"
            "arms_test", "arms", "arm_test", "arm", "befast_arm" -> "Arms Test"
            "speech_test", "speech", "befast_speech" -> "Speech Test"
            else -> raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
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
