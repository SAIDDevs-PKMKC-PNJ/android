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

    // helper kecil buat destructuring 4 nilai
    private data class Quad<A,B,C,D>(val first: A, val second: B, val third: C, val fourth: D)

    inner class VH(private val binding: ItemScreeningDetailBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TestResult) {
            val ctx = binding.root.context

            // Nama tes
            binding.tvTestName.text = mapName(item.testName)

            // Persentase severity 0..100 dari score 0..1 (clamp aman)
            val pct = (item.score.coerceIn(0f, 1f) * 100f).roundToInt().coerceIn(0, 100)

            val (percentText, tintRes, nameColorRes, cd) = when {
                !item.isCompleted -> Triple("—", R.color.GrayLight, R.color.GrayLight) to "Pending"
                item.isSuccessful -> Triple("$pct%", R.color.success_color, R.color.BluePrimary) to "Normal"
                else              -> Triple("$pct%", R.color.warning_color, R.color.BluePrimary) to "Abnormal"
            }.let { (triple, label) -> Quad(triple.first, triple.second, triple.third, label) }

            binding.tvPercent.text = percentText
            binding.tvTestName.setTextColor(ContextCompat.getColor(ctx, nameColorRes))

            // Tint indikator bulat
            ImageViewCompat.setImageTintList(
                binding.dotIndicator,
                ColorStateList.valueOf(ContextCompat.getColor(ctx, tintRes))
            )
            binding.dotIndicator.contentDescription = "Status $cd"

            // (opsional) jika kamu punya TextView notes/time di layout, bisa isi di sini
            // binding.tvNote?.text = item.notes
            // binding.tvTime?.text = item.timestamp
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
