package com.pkm.said.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pkm.said.ScreeningHistoryItem
import com.pkm.said.R

class ListHistoryAdapter(
    private val onItemClick: (ScreeningHistoryItem) -> Unit
) : ListAdapter<ScreeningHistoryItem, ListHistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val viewRiskIndicator: View = itemView.findViewById(R.id.viewRiskIndicator)
        private val tvRiskStatus: TextView = itemView.findViewById(R.id.tvRiskStatus)
        private val tvScorePercent: TextView = itemView.findViewById(R.id.tvScore)
        private val tvDateTime: TextView = itemView.findViewById(R.id.tvDateTime)

        fun bind(item: ScreeningHistoryItem, onItemClick: (ScreeningHistoryItem) -> Unit) {
            tvRiskStatus.text = item.riskStatus
            tvScorePercent.text = "${item.scorePercent}%"
            tvDateTime.text = item.formattedDate

            // Set colors based on risk level
            val (indicatorColor, textColor) = when (item.riskLevel.lowercase()) {
                "low", "rendah" -> Pair("#4CAF50", "#4CAF50") // Green
                "medium", "sedang" -> Pair("#FF9800", "#FF9800") // Orange
                "high", "tinggi" -> Pair("#F44336", "#F44336") // Red
                else -> Pair("#757575", "#757575") // Gray
            }

            viewRiskIndicator.setBackgroundColor(android.graphics.Color.parseColor(indicatorColor))
            tvScorePercent.setTextColor(android.graphics.Color.parseColor(textColor))

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ScreeningHistoryItem>() {
        override fun areItemsTheSame(oldItem: ScreeningHistoryItem, newItem: ScreeningHistoryItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ScreeningHistoryItem, newItem: ScreeningHistoryItem): Boolean {
            return oldItem == newItem
        }
    }
}