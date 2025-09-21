package com.pkm.said.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pkm.said.databinding.ItemScreeningDetailBinding
import com.pkm.said.screening.TestResult
import kotlin.math.roundToInt

class ScreeningDetailAdapter :
    ListAdapter<TestResult, ScreeningDetailAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TestResult>() {
            override fun areItemsTheSame(oldItem: TestResult, newItem: TestResult) =
                oldItem.testName == newItem.testName && oldItem.timestamp == newItem.timestamp

            override fun areContentsTheSame(oldItem: TestResult, newItem: TestResult) =
                oldItem == newItem
        }
    }

    inner class VH(val binding: ItemScreeningDetailBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TestResult) {
            binding.tvTestName.text = mapName(item.testName)
            binding.tvPercent.text = "${(item.score * 100).roundToInt()}%"
            binding.dotIndicator.setColorFilter(
                binding.root.context.getColor(
                    if (item.isSuccessful) com.pkm.said.R.color.risk_low_text
                    else com.pkm.said.R.color.risk_high_text
                )
            )
        }

        private fun mapName(raw: String): String {
            return when (raw.lowercase()) {
                "face_test", "face" -> "Face Test"
                "arms_test", "arms" -> "Arms Test"
                "speech_test", "speech" -> "Speech Test"
                "balance_test", "balance" -> "Balance Test"
                "eyes_test", "eyes", "time_test", "time" -> "Time/Eyes Test"
                else -> raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemScreeningDetailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
}